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
 * Gestion du parc d'entreprises clientes, reservee au proprietaire de la plateforme.
 *
 * Ce service travaille par nature hors cloisonnement : son perimetre est l'ensemble du parc,
 * et le controle d'acces repose entierement sur le {@code @PreAuthorize} des controleurs de
 * l'espace plateforme. C'est aussi pourquoi il renseigne explicitement l'entreprise des
 * entites qu'il cree — {@code TenantEntityListener} ne peut rien deduire d'un contexte vide.
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

    // ── Ouverture d'un compte client ─────────────────────────────────────────

    /**
     * Cree l'entreprise, son administrateur initial et, le cas echeant, son abonnement.
     *
     * L'ensemble tient dans une transaction unique : une entreprise sans administrateur
     * serait un compte inaccessible, impossible a rattraper autrement qu'a la main en base.
     * Si l'une des trois etapes echoue, aucune ne subsiste.
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
        // Affectation explicite : le contexte de cloisonnement est vide cote plateforme,
        // le listener n'a donc aucune entreprise a deduire.
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

        // Apres commit, et non pendant : l'entreprise vient d'etre creee dans cette
        // transaction, une notification ecrite depuis une autre connexion ne la verrait pas
        // encore et buterait sur la cle etrangere — faisant echouer l'ouverture du compte.
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
        // Le slug n'est pas recalcule : il est fige a la creation, un identifiant stable
        // perdant tout interet s'il suit les changements de raison sociale.
        return companyRepository.save(company);
    }

    // ── Cycle de vie commercial ──────────────────────────────────────────────

    /**
     * Suspend l'acces d'une entreprise sans toucher a ses donnees.
     *
     * L'effet est immediat pour tous ses utilisateurs : {@code User.isEnabled()} consulte le
     * statut de l'entreprise, et le filtre JWT le revalide a chaque requete — un jeton emis
     * avant la suspension cesse donc d'etre accepte sans attendre son expiration.
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

    /** Retablit l'acces. L'entreprise repasse en essai si son essai court encore. */
    public Company reactivate(Long id) {
        Company company = getCompanyById(id);
        boolean trialStillRunning = company.getTrialEndsAt() != null
                && company.getTrialEndsAt().isAfter(LocalDateTime.now());
        company.setStatus(trialStillRunning ? Company.CompanyStatus.TRIAL : Company.CompanyStatus.ACTIVE);
        company.setCanceledAt(null);
        return companyRepository.save(company);
    }

    /**
     * Resilie le compte : acces coupe, abonnement clos, donnees conservees.
     *
     * Rien n'est supprime — c'est ce qui rend une reactivation possible et ce qui satisfait
     * les obligations de conservation. La resiliation de l'abonnement est ce qui alimente
     * le churn.
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

    // ── Utilitaires ──────────────────────────────────────────────────────────

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
     * Derive un slug du nom, suffixe d'un compteur en cas de collision.
     *
     * Les accents sont decomposes puis retires plutot que remplaces un a un : « Précision »
     * donne ainsi « precision » sans table de correspondance a maintenir.
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
