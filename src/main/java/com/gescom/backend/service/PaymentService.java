package com.gescom.backend.service;

import com.gescom.backend.config.StripeProperties;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Payment;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.security.CashierScope;
import com.gescom.backend.service.stripe.StripeGateway;
import com.gescom.backend.service.stripe.StripeIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paiement par carte au terminal, en trois étapes :
 * <ol>
 *   <li>{@link #createIntent} : l'intention est créée chez Stripe et enregistrée en base ;</li>
 *   <li>{@link #confirmIntent} : le terminal envoie une carte, Stripe accepte ou refuse ;</li>
 *   <li>paiement : si Stripe accepte, {@link InvoiceService#recordPayment} met la facture à
 *       jour (PARTIALLY_PAID ou PAID), exactement comme un paiement en espèces.</li>
 * </ol>
 *
 * <strong>Statut de la commande.</strong> On ne change aucun statut de commande ici, exprès :
 * le cycle de la commande est linéaire (CONFIRMED → INVOICED → DELIVERED) et une commande
 * facturée est déjà INVOICED quand on arrive au terminal. Le paiement change le statut de sa
 * facture, et c'est de là que vient le « Payée » affiché sur la commande (voir
 * {@code InvoiceService.getInvoiceStatusesByOrderIds}). Ajouter un statut PAID sur la commande
 * mettrait la même info dans deux machines à états différentes.
 *
 * Ce service n'enregistre jamais un paiement lui-même : il passe par {@link InvoiceService},
 * qui reste le seul à appliquer les règles de paiement (facture annulée, montant trop grand…).
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Stripe travaille en centimes : 12,34 € → 1234. */
    private static final BigDecimal CENTS = new BigDecimal("100");

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final ActivityLogService activityLogService;
    private final StripeGateway stripeGateway;
    private final StripeProperties stripeProperties;
    private final CashierScope cashierScope;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService, ActivityLogService activityLogService,
                          StripeGateway stripeGateway, StripeProperties stripeProperties,
                          CashierScope cashierScope) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.activityLogService = activityLogService;
        this.stripeGateway = stripeGateway;
        this.stripeProperties = stripeProperties;
        this.cashierScope = cashierScope;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    /**
     * Session de paiement, visible seulement par le caissier qui a fait la vente
     * (paiement → facture → commande).
     *
     * Si elle n'est pas au caissier, on fait comme si elle n'existait pas au lieu de refuser :
     * contrairement à une commande ou une facture, son id est purement interne et l'interface
     * ne le montre jamais aux autres caissiers, donc il n'y a rien à confirmer à quelqu'un qui
     * le devinerait.
     */
    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return cashierScope.filterReadable(
                        paymentRepository.findById(id), p -> p.getInvoice().getOrder())
                .orElseThrow(() -> new ResourceNotFoundException("payment", id));
    }

    /**
     * Historique des tentatives sur une facture. On relit d'abord la facture pour qu'on ne
     * puisse pas obtenir l'historique d'un collègue juste en donnant son id (sinon la
     * vérification ne porterait que sur la facture elle-même).
     *
     * Si la facture n'est pas au caissier, on fait comme si elle n'existait pas : une 403
     * permettrait, en essayant des ids, de savoir lesquels correspondent aux ventes des collègues.
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByInvoice(Long invoiceId) {
        cashierScope.filterReadable(invoiceRepository.findById(invoiceId), Invoice::getOrder)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", invoiceId));
        return paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    /**
     * Session à modifier. La réponse est la même qu'en lecture (si ce n'est pas au caissier,
     * la session « n'existe pas »), mais on logge la tentative : l'interface ne permet jamais
     * d'arriver sur la session d'un collègue, donc c'est une requête fabriquée ou un bug.
     */
    private Payment getPaymentForWrite(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("payment", id));
        if (!cashierScope.canAccess(payment)) {
            log.warn("Caissier {} : tentative d'écriture sur le paiement {} d'un autre opérateur",
                    cashierScope.restrictedUserId(), payment.getIntentId());
            throw new ResourceNotFoundException("payment", id);
        }
        return payment;
    }

    // ── 1. Création de l'intention ───────────────────────────────────────────

    /**
     * Crée une session de paiement pour une facture.
     *
     * @param requestedAmount montant à encaisser ; {@code null} pour payer tout le reste
     */
    public Payment createIntent(Long invoiceId, BigDecimal requestedAmount) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", invoiceId));
        // On n'encaisse que ses propres ventes : ouvrir une session sur la facture d'un collègue
        // mettrait son paiement sur une autre caisse.
        cashierScope.requireAccess(invoice);

        // Mêmes vérifications que pour le paiement manuel : autant refuser avant d'appeler
        // Stripe plutôt qu'après avoir créé une intention qu'on ne pourra pas utiliser.
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED) {
            throw BusinessException.of("invoice.payment.canceled",
                    "Impossible d'encaisser une facture annulée");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.of("invoice.alreadyPaid", "La facture est déjà payée");
        }

        BigDecimal amount = normalizeAmount(invoice, requestedAmount);

        // Une seule session à confirmer à la fois : avec deux intentions ouvertes sur la même
        // facture, on pourrait encaisser deux fois. On ferme donc les sessions encore ouvertes
        // au lieu de refuser la nouvelle : si le terminal a été fermé avant la fin, la session
        // reste ouverte et personne ne la terminera, et la refuser empêcherait tout paiement
        // carte sur la facture.
        closeOpenSessions(invoiceId);

        StripeIntent intent = stripeGateway.createIntent(
                toCents(amount),
                stripeProperties.getCurrency(),
                "Facture " + invoice.getInvoiceNumber(),
                metadataOf(invoice));

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setIntentId(intent.id());
        payment.setAmount(amount);
        payment.setCurrency(stripeProperties.getCurrency().toUpperCase());
        payment.setStatus(Payment.PaymentStatus.REQUIRES_CONFIRMATION);
        payment.setSimulated(stripeGateway.isSimulated());
        Payment saved = paymentRepository.save(payment);

        logActivity(ActivityLog.ActionType.CREATE, saved.getId(),
                "Intention de paiement " + intent.id() + " de " + amount + " € sur la facture "
                        + invoice.getInvoiceNumber());

        // Le secret n'est pas enregistré : il n'existe que dans cette réponse.
        return withClientSecret(saved, intent.clientSecret());
    }

    // ── 2. Confirmation, puis paiement ───────────────────────────────────────

    /**
     * Confirme l'intention avec une carte de test et, si Stripe accepte, enregistre le
     * paiement sur la facture.
     */
    public Payment confirmIntent(Long paymentId, String paymentMethodId) {
        Payment payment = getPaymentForWrite(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.REQUIRES_CONFIRMATION) {
            throw new BusinessException("Ce paiement n'est plus en attente de confirmation (statut actuel : "
                    + payment.getStatus() + ")");
        }

        // Le reste à payer a pu changer entre l'ouverture de la session et l'envoi de la carte
        // (paiement partiel en espèces entre-temps). On le revérifie avant d'appeler Stripe :
        // après, l'argent serait pris, et si recordPayment refusait, on annulerait la
        // transaction locale mais pas le débit.
        //
        // On ferme alors la session et on *renvoie* le résultat, sans lancer d'exception : une
        // BusinessException annulerait la transaction (donc aussi le passage à CANCELED juste
        // au-dessus) et laisserait la session ouverte, ce qui bloquerait les essais suivants.
        // C'est le même traitement que pour une carte refusée plus bas.
        Invoice invoice = payment.getInvoice();
        BigDecimal remaining = remainingOf(invoice);
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED
                || invoice.getStatus() == Invoice.InvoiceStatus.PAID
                || payment.getAmount().compareTo(remaining) > 0) {
            abandonAtGateway(payment);
            payment.setStatus(Payment.PaymentStatus.CANCELED);
            payment.setFailureMessage("La facture a changé depuis l'ouverture du paiement "
                    + "(reste dû : " + remaining + " €) - relancez le paiement");
            Payment stale = paymentRepository.save(payment);
            logActivity(ActivityLog.ActionType.UPDATE, stale.getId(),
                    "Paiement " + stale.getIntentId() + " abandonné : facture "
                            + invoice.getInvoiceNumber() + " modifiée depuis l'ouverture");
            return stale;
        }

        StripeIntent result = stripeGateway.confirmIntent(payment.getIntentId(), paymentMethodId);

        payment.setCardBrand(result.cardBrand());
        payment.setCardLast4(result.cardLast4());
        payment.setStatus(result.status());
        payment.setConfirmedAt(LocalDateTime.now());

        if (result.status() != Payment.PaymentStatus.SUCCEEDED) {
            payment.setFailureMessage(result.failureMessage());
            Payment refused = paymentRepository.save(payment);
            logActivity(ActivityLog.ActionType.PAYMENT, refused.getId(),
                    "Paiement carte refusé sur la facture " + invoice.getInvoiceNumber()
                            + " : " + result.failureMessage());
            return refused;
        }

        // 3. Stripe a accepté : le paiement suit la même règle que tous les autres, y compris
        // le passage UNPAID → PARTIALLY_PAID → PAID.
        Invoice settled = invoiceService.recordPayment(
                invoice.getId(), payment.getAmount(), Invoice.PaymentMethod.CREDIT_CARD);

        Payment succeeded = paymentRepository.save(payment);
        logActivity(ActivityLog.ActionType.PAYMENT, succeeded.getId(),
                "Paiement carte de " + payment.getAmount() + " € accepté sur la facture "
                        + settled.getInvoiceNumber() + " (" + payment.getIntentId() + ")");
        log.info("Facture {} : {} après paiement carte {}", settled.getInvoiceNumber(),
                settled.getStatus(), payment.getIntentId());

        succeeded.setInvoice(settled);
        return succeeded;
    }

    // ── Annulation ───────────────────────────────────────────────────────────

    public Payment cancelIntent(Long paymentId) {
        Payment payment = getPaymentForWrite(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.REQUIRES_CONFIRMATION) {
            throw BusinessException.of("payment.cancel.onlyPending",
                    "Seul un paiement en attente de confirmation peut être annulé");
        }

        // On essaie d'annuler chez Stripe, sans bloquer si ça échoue : le caissier a demandé
        // l'annulation, une coupure réseau ne doit pas laisser la session ouverte chez nous.
        abandonAtGateway(payment);
        payment.setStatus(Payment.PaymentStatus.CANCELED);
        Payment canceled = paymentRepository.save(payment);

        logActivity(ActivityLog.ActionType.UPDATE, canceled.getId(),
                "Paiement " + payment.getIntentId() + " abandonné depuis le terminal");
        return canceled;
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    /**
     * Ferme les sessions encore ouvertes sur une facture. Une session ouverte ne veut pas dire
     * qu'un paiement est en cours, mais presque toujours qu'un terminal a été fermé avant la
     * fin : elle ne sert à rien et seule la plus récente doit pouvoir être confirmée.
     */
    private void closeOpenSessions(Long invoiceId) {
        for (Payment pending : paymentRepository.findByInvoiceIdAndStatus(
                invoiceId, Payment.PaymentStatus.REQUIRES_CONFIRMATION)) {
            abandonAtGateway(pending);
            pending.setStatus(Payment.PaymentStatus.CANCELED);
            pending.setFailureMessage("Session abandonnée : une nouvelle a été ouverte sur cette facture");
            paymentRepository.save(pending);
            log.info("Paiement {} refermé au profit d'une nouvelle session sur la facture {}",
                    pending.getIntentId(), invoiceId);
        }
    }

    /**
     * Ferme l'intention chez Stripe si possible : c'est l'état chez nous qui compte. Une
     * coupure réseau ne doit pas faire échouer (donc annuler) la transaction qui vient de
     * fermer la session de notre côté.
     */
    private void abandonAtGateway(Payment payment) {
        try {
            stripeGateway.cancelIntent(payment.getIntentId());
        } catch (RuntimeException e) {
            log.warn("Abandon de l'intention {} impossible chez le prestataire : {}",
                    payment.getIntentId(), e.getMessage());
        }
    }

    /** Montant demandé (ou reste à payer par défaut), arrondi et vérifié. */
    private BigDecimal normalizeAmount(Invoice invoice, BigDecimal requestedAmount) {
        BigDecimal remaining = remainingOf(invoice);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of("invoice.nothingLeftToPay",
                    "Cette facture n'a plus de reste à payer");
        }
        if (requestedAmount == null) {
            return remaining;
        }
        BigDecimal amount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of("payment.amount.positive",
                    "Le montant du paiement doit être positif");
        }
        if (amount.compareTo(remaining) > 0) {
            throw BusinessException.of("payment.amount.exceedsRemaining",
                    "Le montant dépasse le reste à payer (" + remaining + " €)", remaining);
        }
        return amount;
    }

    private BigDecimal remainingOf(Invoice invoice) {
        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        return invoice.getTotalAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(CENTS).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Infos métier ajoutées à la transaction, pour retrouver d'où elle vient côté Stripe. */
    private Map<String, String> metadataOf(Invoice invoice) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("invoiceId", String.valueOf(invoice.getId()));
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        Order order = invoice.getOrder();
        if (order != null) {
            metadata.put("orderId", String.valueOf(order.getId()));
            metadata.put("orderNumber", order.getOrderNumber());
        }
        return metadata;
    }

    /**
     * Ajoute le secret client à l'objet renvoyé sans l'enregistrer : l'entité est détachée du
     * contexte de persistance juste pour cette réponse HTTP.
     */
    private Payment withClientSecret(Payment payment, String clientSecret) {
        payment.setClientSecret(clientSecret);
        return payment;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, Long paymentId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, "Payment", paymentId, description, null, null);
            }
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }
}
