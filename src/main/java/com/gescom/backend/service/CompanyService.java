package com.gescom.backend.service;

import com.gescom.backend.dto.platform.CompanyProvisionRequest;
import com.gescom.backend.dto.platform.CompanyRequest;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.Plan;
import com.gescom.backend.entity.PlatformNotification;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.PlanRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Gestion des entreprises clientes, reservee au super admin.
 *
 * Ce service travaille forcement sans filtre par entreprise : il porte sur toutes les
 * entreprises, et l'acces est controle uniquement par le {@code @PreAuthorize} des
 * controleurs de l'espace plateforme. C'est aussi pour ca qu'il remplit lui-meme
 * l'entreprise des entites qu'il cree : {@code TenantEntityListener} ne peut rien deviner
 * avec un contexte vide.
 */
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformNotificationService notificationService;

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository,
                          PlanRepository planRepository, SubscriptionService subscriptionService,
                          PasswordEncoder passwordEncoder,
                          PlatformNotificationService notificationService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    // ── Lectures ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Company> getCompanies(Pageable pageable) {
        return companyRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Company getCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    @Transactional(readOnly = true)
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    // ── Creation d'un compte client ──────────────────────────────────────────

    /**
     * Cree l'entreprise, son premier admin et, si besoin, son abonnement.
     *
     * Le tout dans une seule transaction : une entreprise sans admin serait inaccessible, et
     * on ne pourrait la reparer qu'a la main en base. Si une des trois etapes echoue, rien
     * n'est garde.
     */
    public Company provision(CompanyProvisionRequest request) {
        Company company = createCompany(request.company());

        CompanyProvisionRequest.InitialAdmin admin = request.admin();
        if (userRepository.existsByUsername(admin.username())) {
            throw new DuplicateResourceException("User", "username", admin.username());
        }
        if (userRepository.existsByEmail(admin.email())) {
            throw new DuplicateResourceException("User", "email", admin.email());
        }

        User owner = new User();
        owner.setUsername(admin.username());
        owner.setEmail(admin.email());
        owner.setPassword(passwordEncoder.encode(admin.password()));
        owner.setFirstName(admin.firstName());
        owner.setLastName(admin.lastName());
        owner.setRole(User.Role.ADMIN);
        owner.setActive(true);
        // On met l'entreprise a la main : cote plateforme le contexte est vide, donc le
        // listener ne peut pas la deviner.
        owner.setOwnerCompany(company);
        userRepository.save(owner);

        if (request.planId() != null) {
            Plan plan = planRepository.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", request.planId()));
            boolean trial = Boolean.TRUE.equals(request.startTrial());
            Subscription.BillingPeriod period = parsePeriod(request.billingPeriod());
            subscriptionService.subscribe(company, plan, period, null, trial);

            if (trial) {
                company.setStatus(Company.CompanyStatus.TRIAL);
                company.setTrialEndsAt(LocalDateTime.now().plusDays(plan.getTrialDays()));
            } else {
                company.setStatus(Company.CompanyStatus.ACTIVE);
                company.setTrialEndsAt(null);
            }
        }

        // Apres le commit, pas pendant : l'entreprise vient d'etre creee dans cette
        // transaction, une notification ecrite depuis une autre connexion ne la verrait pas
        // encore et planterait sur la cle etrangere, ce qui ferait echouer la creation.
        notificationService.recordAfterCommit("COMPANY_PROVISIONED", PlatformNotification.Severity.INFO,
                "Nouveau client : " + company.getName(),
                "Compte ouvert avec l'administrateur " + owner.getUsername(),
                company, "Company", company.getId());

        return company;
    }

    public Company createCompany(CompanyRequest request) {
        if (companyRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Company", "email", request.email());
        }
        Company company = new Company();
        apply(company, request);
        company.setSlug(uniqueSlug(request.name()));
        company.setStatus(Company.CompanyStatus.TRIAL);
        return companyRepository.save(company);
    }

    public Company updateCompany(Long id, CompanyRequest request) {
        Company company = getCompanyById(id);
        if (!company.getEmail().equalsIgnoreCase(request.email())
                && companyRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Company", "email", request.email());
        }
        apply(company, request);
        // On ne recalcule pas le slug : il est fixe a la creation, un identifiant stable ne
        // sert a rien s'il change avec la raison sociale.
        return companyRepository.save(company);
    }

    // ── Etat commercial ──────────────────────────────────────────────────────

    /**
     * Bloque l'acces d'une entreprise sans toucher a ses donnees.
     *
     * Ca s'applique tout de suite a tous ses utilisateurs : {@code User.isEnabled()} regarde
     * le statut de l'entreprise, et le filtre JWT le reverifie a chaque requete. Un token cree
     * avant la suspension ne marche donc plus, sans attendre qu'il expire.
     */
    public Company suspend(Long id, String reason) {
        Company company = getCompanyById(id);
        if (company.getStatus() == Company.CompanyStatus.CANCELED) {
            throw BusinessException.of("company.canceled.cannotSuspend",
                    "Une entreprise resiliee ne peut pas etre suspendue");
        }
        company.setStatus(Company.CompanyStatus.SUSPENDED);
        if (reason != null && !reason.isBlank()) {
            company.setNotes(reason);
        }
        notificationService.record("COMPANY_SUSPENDED", PlatformNotification.Severity.WARNING,
                "Compte suspendu : " + company.getName(),
                reason, company, "Company", company.getId());
        return companyRepository.save(company);
    }

    /** Redonne l'acces. L'entreprise repasse en essai si sa periode d'essai n'est pas finie. */
    public Company reactivate(Long id) {
        Company company = getCompanyById(id);
        boolean trialStillRunning = company.getTrialEndsAt() != null
                && company.getTrialEndsAt().isAfter(LocalDateTime.now());
        company.setStatus(trialStillRunning ? Company.CompanyStatus.TRIAL : Company.CompanyStatus.ACTIVE);
        company.setCanceledAt(null);
        return companyRepository.save(company);
    }

    /**
     * Resilie le compte : acces bloque, abonnement ferme, donnees gardees.
     *
     * On ne supprime rien : c'est ce qui permet de reactiver le compte, et on respecte les
     * obligations de conservation. C'est la resiliation de l'abonnement qui compte dans le
     * churn.
     */
    public Company cancel(Long id, String reason) {
        Company company = getCompanyById(id);
        company.setStatus(Company.CompanyStatus.CANCELED);
        company.setCanceledAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            company.setNotes(reason);
        }
        subscriptionService.cancelForCompany(company.getId(), reason);
        notificationService.record("COMPANY_CANCELED", PlatformNotification.Severity.CRITICAL,
                "Resiliation : " + company.getName(),
                reason, company, "Company", company.getId());
        return companyRepository.save(company);
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    private void apply(Company company, CompanyRequest request) {
        company.setName(request.name());
        company.setEmail(request.email());
        company.setPhone(request.phone());
        company.setAddress(request.address());
        company.setCity(request.city());
        company.setPostalCode(request.postalCode());
        company.setCountry(request.country() != null && !request.country().isBlank()
                ? request.country() : "Belgique");
        company.setTaxId(request.taxId());
        company.setNotes(request.notes());
    }

    /**
     * Cree un slug a partir du nom, avec un numero a la fin s'il existe deja.
     *
     * Les accents sont separes puis supprimes plutot que remplaces un par un : « Précision »
     * donne « precision » sans avoir de table de correspondance a maintenir.
     */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (base.isBlank()) {
            base = "entreprise";
        }
        if (base.length() > 70) {
            base = base.substring(0, 70).replaceAll("-+$", "");
        }
        String candidate = base;
        int suffix = 2;
        while (companyRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private Subscription.BillingPeriod parsePeriod(String value) {
        if (value == null || value.isBlank()) {
            return Subscription.BillingPeriod.MONTHLY;
        }
        try {
            return Subscription.BillingPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.of("subscription.billingPeriod.invalid",
                    "Periodicite de facturation invalide : " + value, value);
        }
    }
}
