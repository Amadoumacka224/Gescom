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
 * Parcours de paiement par carte du terminal, en trois temps :
 * <ol>
 *   <li>{@link #createIntent} — l'intention est ouverte chez le prestataire et tracée en base ;</li>
 *   <li>{@link #confirmIntent} — le terminal présente une carte, le prestataire tranche ;</li>
 *   <li>encaissement — si le prestataire accepte, {@link InvoiceService#recordPayment} met la
 *       facture à jour (PARTIALLY_PAID ou PAID) exactement comme un règlement en espèces.</li>
 * </ol>
 *
 * <strong>Statut de la commande.</strong> Aucune transition n'est déclenchée ici, et c'est
 * voulu : le cycle de vie de l'Order est linéaire (CONFIRMED → INVOICED → DELIVERED) et une
 * commande facturée est déjà INVOICED quand le terminal entre en jeu. Ce que le paiement fait
 * évoluer, c'est le statut de sa facture — dont l'application déduit le « Payée » affiché sur
 * la commande (voir {@code InvoiceService.getInvoiceStatusesByOrderIds}). Introduire un statut
 * PAID sur l'Order dupliquerait cette information dans deux machines à états concurrentes.
 *
 * Le service n'encaisse jamais de sa propre autorité : il délègue à {@link InvoiceService},
 * qui reste seul juge des règles de règlement (facture annulée, dépassement du reste dû…).
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Stripe raisonne en plus petite unité monétaire : 12,34 € → 1234. */
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
     * Session de paiement, cloisonnée sur le caissier qui a saisi la vente d'origine
     * (payment → facture → commande).
     *
     * Hors périmètre, la session est traitée comme inexistante plutôt que refusée : contrairement
     * à une commande ou une facture, son identifiant n'est qu'une poignée interne que l'interface
     * n'expose jamais entre opérateurs — il n'y a donc rien à confirmer à qui la devinerait.
     */
    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return cashierScope.filterReadable(
                        paymentRepository.findById(id), p -> p.getInvoice().getOrder())
                .orElseThrow(() -> new ResourceNotFoundException("payment", id));
    }

    /**
     * Historique des tentatives d'une facture. La facture est relue au préalable pour que
     * l'historique d'un collègue ne puisse pas être obtenu en donnant simplement son
     * identifiant — le cloisonnement ne porterait sinon que sur la facture elle-même.
     *
     * Hors périmètre, la facture est traitée comme inexistante : un 403 confirmerait à qui
     * balaierait les identifiants lesquels correspondent aux ventes de ses collègues.
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByInvoice(Long invoiceId) {
        cashierScope.filterReadable(invoiceRepository.findById(invoiceId), Invoice::getOrder)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", invoiceId));
        return paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    /**
     * Session à modifier. La réponse reste celle de la lecture — hors périmètre, la session est
     * inexistante — mais la tentative, elle, est tracée : l'interface ne mène jamais à la session
     * d'un collègue, un tel appel traduit une requête forgée ou une régression.
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
     * Ouvre une session de paiement pour une facture.
     *
     * @param requestedAmount montant à encaisser ; {@code null} pour solder le reste dû
     */
    public Payment createIntent(Long invoiceId, BigDecimal requestedAmount) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", invoiceId));
        // On n'encaisse que ses propres ventes : ouvrir une session sur la facture d'un collègue
        // porterait son encaissement au crédit d'une autre caisse.
        cashierScope.requireAccess(invoice);

        // Mêmes gardes que l'encaissement manuel — autant refuser avant d'appeler Stripe
        // qu'après avoir créé une intention qu'on ne pourra pas honorer.
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED) {
            throw BusinessException.of("invoice.payment.canceled",
                    "Impossible d'encaisser une facture annulée");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.of("invoice.alreadyPaid", "La facture est déjà payée");
        }

        BigDecimal amount = normalizeAmount(invoice, requestedAmount);

        // Une seule session confirmable à la fois : deux intentions ouvertes sur la même facture
        // mèneraient à un double encaissement au moment des confirmations. On referme donc les
        // sessions restées ouvertes plutôt que de refuser la nouvelle — un terminal fermé avant
        // la fin du parcours laisse une session pendante que personne ne viendra solder, et la
        // refuser priverait alors la facture de tout paiement carte.
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

        // Le secret n'est pas persisté : il ne vit que le temps de cette réponse.
        return withClientSecret(saved, intent.clientSecret());
    }

    // ── 2. Confirmation, puis encaissement ───────────────────────────────────

    /**
     * Confirme l'intention avec un moyen de paiement de test et, si le prestataire accepte,
     * enregistre l'encaissement sur la facture.
     */
    public Payment confirmIntent(Long paymentId, String paymentMethodId) {
        Payment payment = getPaymentForWrite(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.REQUIRES_CONFIRMATION) {
            throw new BusinessException("Ce paiement n'est plus en attente de confirmation (statut actuel : "
                    + payment.getStatus() + ")");
        }

        // Le reste dû a pu changer entre l'ouverture de la session et la carte présentée
        // (encaissement partiel en espèces entre-temps). On le revérifie avant d'appeler le
        // prestataire : après, l'argent serait pris et le rejet de recordPayment annulerait
        // la transaction locale sans annuler le débit.
        //
        // La session est alors close, et l'issue est *retournée*, pas levée : lever une
        // BusinessException annulerait la transaction — donc le passage à CANCELED juste
        // au-dessus — et laisserait la session ouverte, bloquant les tentatives suivantes.
        // C'est exactement le traitement réservé à un refus d'émetteur plus bas.
        Invoice invoice = payment.getInvoice();
        BigDecimal remaining = remainingOf(invoice);
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED
                || invoice.getStatus() == Invoice.InvoiceStatus.PAID
                || payment.getAmount().compareTo(remaining) > 0) {
            abandonAtGateway(payment);
            payment.setStatus(Payment.PaymentStatus.CANCELED);
            payment.setFailureMessage("La facture a changé depuis l'ouverture du paiement "
                    + "(reste dû : " + remaining + " €) — relancez le paiement");
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

        // 3. Le prestataire a accepté : l'encaissement suit la même règle métier que tout
        // autre règlement, y compris la bascule UNPAID → PARTIALLY_PAID → PAID.
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

    // ── Abandon ──────────────────────────────────────────────────────────────

    public Payment cancelIntent(Long paymentId) {
        Payment payment = getPaymentForWrite(paymentId);

        if (payment.getStatus() != Payment.PaymentStatus.REQUIRES_CONFIRMATION) {
            throw BusinessException.of("payment.cancel.onlyPending",
                    "Seul un paiement en attente de confirmation peut être annulé");
        }

        // Au mieux côté prestataire : le caissier a demandé l'abandon, une panne réseau ne doit
        // pas laisser la session ouverte de notre côté.
        abandonAtGateway(payment);
        payment.setStatus(Payment.PaymentStatus.CANCELED);
        Payment canceled = paymentRepository.save(payment);

        logActivity(ActivityLog.ActionType.UPDATE, canceled.getId(),
                "Paiement " + payment.getIntentId() + " abandonné depuis le terminal");
        return canceled;
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    /**
     * Referme les sessions encore ouvertes sur une facture. Une session pendante n'est pas le
     * signe d'un encaissement en cours mais, presque toujours, d'un terminal fermé en route :
     * elle n'a aucune valeur et seule la plus récente doit rester confirmable.
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
     * Clôture l'intention chez le prestataire, au mieux : ce qui fait foi est l'état local.
     * Une panne réseau ne doit pas faire échouer — donc annuler — la transaction qui vient de
     * marquer la session close de notre côté.
     */
    private void abandonAtGateway(Payment payment) {
        try {
            stripeGateway.cancelIntent(payment.getIntentId());
        } catch (RuntimeException e) {
            log.warn("Abandon de l'intention {} impossible chez le prestataire : {}",
                    payment.getIntentId(), e.getMessage());
        }
    }

    /** Montant demandé (ou reste dû par défaut), normalisé et validé. */
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

    /** Références métier attachées à la transaction, pour retrouver l'origine côté Stripe. */
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
     * Attache le secret client à l'objet renvoyé sans le stocker : l'entité est détachée du
     * contexte de persistance pour ce seul aller-retour HTTP.
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
