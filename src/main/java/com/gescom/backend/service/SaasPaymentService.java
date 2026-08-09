package com.gescom.backend.service;

import com.gescom.backend.dto.platform.SaasPaymentRequest;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.PlatformNotification;
import com.gescom.backend.entity.SaasPayment;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.SaasPaymentRepository;
import com.gescom.backend.repository.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Journal des encaissements d'abonnement.
 *
 * Les echecs sont enregistres comme les succes : c'est ce qui rend le taux de reussite et le
 * suivi des impayes calculables. Un paiement refuse fait de surcroit basculer l'abonnement en
 * PAST_DUE — le contrat reste vivant et continue de compter dans le MRR, mais l'anomalie
 * remonte au tableau de bord.
 */
@Service
@Transactional
public class SaasPaymentService {

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final SaasPaymentRepository saasPaymentRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PlatformNotificationService notificationService;

    public SaasPaymentService(SaasPaymentRepository saasPaymentRepository,
                              CompanyRepository companyRepository,
                              SubscriptionRepository subscriptionRepository,
                              SubscriptionService subscriptionService,
                              PlatformNotificationService notificationService) {
        this.saasPaymentRepository = saasPaymentRepository;
        this.companyRepository = companyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<SaasPayment> getPayments(SaasPayment.SaasPaymentStatus status, Long companyId, Pageable pageable) {
        if (companyId != null) {
            return saasPaymentRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
        }
        return status == null
                ? saasPaymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                : saasPaymentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public SaasPayment getById(Long id) {
        return saasPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SaasPayment", id));
    }

    /**
     * Enregistre un encaissement.
     *
     * Un paiement abouti renouvelle la periode de l'abonnement rattache, et un echec le fait
     * passer en impaye : le journal et l'etat du contrat restent ainsi coherents sans que
     * l'operateur ait a faire les deux gestes.
     */
    public SaasPayment record(SaasPaymentRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));

        Subscription subscription = null;
        if (request.subscriptionId() != null) {
            subscription = subscriptionRepository.findById(request.subscriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription", request.subscriptionId()));
            if (!subscription.getCompany().getId().equals(company.getId())) {
                throw BusinessException.of("saasPayment.subscriptionMismatch",
                        "L'abonnement indique n'appartient pas a cette entreprise");
            }
        } else {
            subscription = subscriptionService.getLiveForCompany(company.getId()).orElse(null);
        }

        SaasPayment.SaasPaymentStatus status = parse(SaasPayment.SaasPaymentStatus.class, request.status(),
                "saasPayment.status.invalid", "Statut de paiement invalide : ");
        SaasPayment.PaymentMethod method = parse(SaasPayment.PaymentMethod.class, request.method(),
                "saasPayment.method.invalid", "Moyen de paiement invalide : ");

        SaasPayment payment = new SaasPayment();
        payment.setCompany(company);
        payment.setSubscription(subscription);
        payment.setReference(nextReference(company.getId()));
        payment.setAmount(request.amount());
        payment.setStatus(status);
        payment.setMethod(method);
        payment.setFailureMessage(request.failureMessage());

        if (subscription != null) {
            payment.setCurrency(subscription.getCurrency());
            payment.setPeriodStart(subscription.getCurrentPeriodStart());
            payment.setPeriodEnd(subscription.getCurrentPeriodEnd());
        }

        if (status == SaasPayment.SaasPaymentStatus.SUCCEEDED) {
            payment.setPaidAt(LocalDateTime.now());
        }

        SaasPayment saved = saasPaymentRepository.save(payment);

        if (subscription != null) {
            if (status == SaasPayment.SaasPaymentStatus.SUCCEEDED && subscription.isLive()) {
                subscriptionService.renew(subscription.getId());
            } else if (status == SaasPayment.SaasPaymentStatus.FAILED && subscription.isLive()) {
                subscriptionService.markPastDue(subscription.getId());
            }
        }

        if (status == SaasPayment.SaasPaymentStatus.FAILED) {
            notificationService.record("PAYMENT_FAILED", PlatformNotification.Severity.CRITICAL,
                    "Paiement refuse : " + company.getName(),
                    saved.getReference() + (request.failureMessage() != null
                            ? " — " + request.failureMessage() : ""),
                    company, "SaasPayment", saved.getId());
        }
        return saved;
    }

    /**
     * Reference unique et lisible : SP-<periode>-<entreprise>-<compteur du mois>.
     *
     * Le compteur repart de 1 chaque mois pour chaque entreprise, ce qui donne une reference
     * parlante en support (« SP-202608-0003-2 » se lit sans consulter la base). La boucle
     * couvre le cas ou une reference serait deja prise, y compris apres suppression.
     */
    private String nextReference(Long companyId) {
        String prefix = "SP-" + LocalDateTime.now().format(PERIOD)
                + "-" + String.format("%04d", companyId);
        int sequence = 1;
        String candidate = prefix + "-" + sequence;
        while (saasPaymentRepository.existsByReference(candidate)) {
            candidate = prefix + "-" + (++sequence);
        }
        return candidate;
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String key, String fallback) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.of(key, fallback + value, value);
        }
    }
}
