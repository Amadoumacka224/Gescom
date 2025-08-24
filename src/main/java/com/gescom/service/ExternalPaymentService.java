package com.gescom.service;

import com.gescom.entity.ExternalPayment;
import com.gescom.entity.Invoice;
import com.gescom.repository.ExternalPaymentRepository;
import com.gescom.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExternalPaymentService {

    private final ExternalPaymentRepository externalPaymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final StripePaymentService stripePaymentService;
    private final SecurityService securityService;
    private final PaymentNotificationService notificationService;

    private static final int TOKEN_LENGTH = 32;
    private static final int SESSION_EXPIRY_HOURS = 1;

    /**
     * Recherche une facture par son numéro pour paiement externe
     */
    @Transactional(readOnly = true)
    public Optional<Invoice> findInvoiceForPayment(String invoiceNumber) {
        log.info("Recherche de facture pour paiement externe: {}", invoiceNumber);
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        
        if (invoiceOpt.isEmpty()) {
            log.warn("Facture non trouvée: {}", invoiceNumber);
            return Optional.empty();
        }
        
        Invoice invoice = invoiceOpt.get();
        
        // Vérifications de sécurité
        if (!isInvoicePayable(invoice)) {
            log.warn("Facture non payable: {} - Statut: {}", invoiceNumber, invoice.getStatus());
            return Optional.empty();
        }
        
        return Optional.of(invoice);
    }

    /**
     * Vérifie si une facture peut être payée
     */
    private boolean isInvoicePayable(Invoice invoice) {
        return invoice.getStatus() == Invoice.InvoiceStatus.DRAFT ||
               invoice.getStatus() == Invoice.InvoiceStatus.SENT ||
               invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE ||
               invoice.getStatus() == Invoice.InvoiceStatus.PARTIAL;
    }

    /**
     * Initie un processus de paiement externe (version complète)
     */
    public ExternalPayment initiatePayment(Invoice invoice, 
                                         ExternalPayment.PaymentMethod paymentMethod,
                                         BigDecimal amount,
                                         String customerEmail,
                                         String customerName,
                                         String clientIp,
                                         String userAgent) {
        
        log.info("Initiation paiement externe pour facture: {} - Montant: {} - Méthode: {}", 
                invoice.getInvoiceNumber(), amount, paymentMethod);

        // Validations de sécurité
        validatePaymentAmount(invoice, amount);
        securityService.validateClientRequest(clientIp, userAgent);

        ExternalPayment payment = new ExternalPayment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setCustomerEmail(customerEmail);
        payment.setCustomerName(customerName);
        payment.setClientIp(clientIp);
        payment.setUserAgent(userAgent);
        payment.setSecurityToken(generateSecureToken());
        payment.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS));
        payment.setGatewayProvider("STRIPE");

        ExternalPayment savedPayment = externalPaymentRepository.save(payment);

        log.info("Paiement externe initié avec ID: {} et token: {}", 
                savedPayment.getId(), savedPayment.getSecurityToken());

        return savedPayment;
    }

    /**
     * Initie un processus de paiement externe (version simplifiée)
     */
    public ExternalPayment initiatePayment(Invoice invoice, 
                                         ExternalPayment.PaymentMethod paymentMethod,
                                         BigDecimal amount,
                                         String customerEmail,
                                         String customerName,
                                         String clientIp) {
        return initiatePayment(invoice, paymentMethod, amount, customerEmail, customerName, clientIp, null);
    }

    /**
     * Sauvegarde un paiement externe
     */
    public ExternalPayment savePayment(ExternalPayment payment) {
        return externalPaymentRepository.save(payment);
    }

    /**
     * Crée une session de paiement Stripe
     */
    public String createStripePaymentSession(ExternalPayment payment) throws Exception {
        log.info("Création session Stripe pour paiement: {}", payment.getId());

        String sessionId = stripePaymentService.createPaymentSession(
                payment.getAmount(),
                "EUR",
                payment.getInvoice().getInvoiceNumber(),
                payment.getCustomerEmail(),
                payment.getSecurityToken()
        );

        payment.setGatewaySessionId(sessionId);
        externalPaymentRepository.save(payment);

        log.info("Session Stripe créée: {} pour paiement: {}", sessionId, payment.getId());
        return sessionId;
    }

    /**
     * Traite un webhook de Stripe
     */
    public void handleStripeWebhook(String eventType, String sessionId, Object eventData) {
        log.info("Traitement webhook Stripe: {} pour session: {}", eventType, sessionId);

        Optional<ExternalPayment> paymentOpt = externalPaymentRepository.findByGatewaySessionId(sessionId);
        
        if (paymentOpt.isEmpty()) {
            log.warn("Paiement non trouvé pour session Stripe: {}", sessionId);
            return;
        }

        ExternalPayment payment = paymentOpt.get();

        switch (eventType) {
            case "checkout.session.completed":
                handlePaymentSuccess(payment, eventData);
                break;
            case "payment_intent.payment_failed":
                handlePaymentFailure(payment, "Paiement échoué côté Stripe");
                break;
            case "payment_intent.canceled":
                handlePaymentCancellation(payment);
                break;
            default:
                log.info("Événement Stripe ignoré: {}", eventType);
        }
    }

    /**
     * Traite un paiement réussi
     */
    private void handlePaymentSuccess(ExternalPayment payment, Object eventData) {
        log.info("Traitement paiement réussi pour: {}", payment.getId());

        payment.markAsCompleted();
        
        // Extraction des données de paiement depuis Stripe
        // (implémentation spécifique selon la structure des données Stripe)
        
        externalPaymentRepository.save(payment);
        
        // Mise à jour de la facture
        updateInvoicePaymentStatus(payment);
        
        // Envoi des notifications
        notificationService.sendPaymentConfirmation(payment);
        notificationService.sendInvoicePaidNotification(payment.getInvoice(), payment);
        
        log.info("Paiement traité avec succès pour facture: {}", 
                payment.getInvoice().getInvoiceNumber());
    }

    /**
     * Traite un paiement échoué
     */
    private void handlePaymentFailure(ExternalPayment payment, String reason) {
        log.warn("Échec paiement pour: {} - Raison: {}", payment.getId(), reason);
        
        payment.markAsFailed(reason);
        externalPaymentRepository.save(payment);
        
        // Notification d'échec au client
        notificationService.sendPaymentFailureNotification(payment);
        
        // Enregistrement pour la sécurité
        securityService.recordFailedPaymentAttempt(payment.getClientIp(), reason);
    }

    /**
     * Traite l'annulation d'un paiement
     */
    private void handlePaymentCancellation(ExternalPayment payment) {
        log.info("Annulation paiement pour: {}", payment.getId());
        
        payment.setStatus(ExternalPayment.PaymentStatus.CANCELLED);
        externalPaymentRepository.save(payment);
    }

    /**
     * Met à jour le statut de paiement de la facture
     */
    private void updateInvoicePaymentStatus(ExternalPayment payment) {
        Invoice invoice = payment.getInvoice();
        
        // Calcul du montant total payé
        BigDecimal totalPaid = externalPaymentRepository
                .findSuccessfulPaymentsByInvoice(invoice)
                .stream()
                .map(ExternalPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Ajouter les paiements internes existants
        if (invoice.getPaidAmount() != null) {
            totalPaid = totalPaid.add(invoice.getPaidAmount());
        }
        
        invoice.setPaidAmount(totalPaid);
        invoice.setPaymentDate(payment.getCompletedAt().toLocalDate());
        invoice.setPaymentMethod(Invoice.PaymentMethod.CARD);
        invoice.setPaymentReference(payment.getGatewayTransactionId());
        
        // Mise à jour du statut
        if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIAL);
        }
        
        invoiceRepository.save(invoice);
        
        log.info("Facture {} mise à jour - Montant payé: {} - Statut: {}", 
                invoice.getInvoiceNumber(), totalPaid, invoice.getStatus());
    }

    /**
     * Valide le montant du paiement
     */
    private void validatePaymentAmount(Invoice invoice, BigDecimal amount) {
        BigDecimal remainingAmount = invoice.getRemainingAmount();
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du paiement doit être positif");
        }
        
        if (amount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Le montant du paiement ne peut pas dépasser le montant restant dû");
        }
    }

    /**
     * Génère un token sécurisé
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        random.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Récupère un paiement par son token
     */
    @Transactional(readOnly = true)
    public Optional<ExternalPayment> findBySecurityToken(String token) {
        return externalPaymentRepository.findBySecurityToken(token);
    }

    /**
     * Nettoie les paiements expirés
     */
    @Transactional
    public void cleanupExpiredPayments() {
        List<ExternalPayment> expiredPayments = externalPaymentRepository
                .findExpiredPayments(LocalDateTime.now());
        
        for (ExternalPayment payment : expiredPayments) {
            payment.setStatus(ExternalPayment.PaymentStatus.CANCELLED);
            payment.setFailureReason("Session expirée");
        }
        
        if (!expiredPayments.isEmpty()) {
            externalPaymentRepository.saveAll(expiredPayments);
            log.info("Nettoyage de {} paiements expirés", expiredPayments.size());
        }
    }

    /**
     * Obtient les statistiques de paiement
     */
    @Transactional(readOnly = true)
    public PaymentStatistics getPaymentStatistics() {
        long totalPayments = externalPaymentRepository.count();
        long successfulPayments = externalPaymentRepository.countByStatus(ExternalPayment.PaymentStatus.SUCCEEDED);
        long failedPayments = externalPaymentRepository.countByStatus(ExternalPayment.PaymentStatus.FAILED);
        long pendingPayments = externalPaymentRepository.countByStatus(ExternalPayment.PaymentStatus.PENDING);
        
        return new PaymentStatistics(totalPayments, successfulPayments, failedPayments, pendingPayments);
    }

    /**
     * Classe pour les statistiques de paiement
     */
    public record PaymentStatistics(
            long totalPayments,
            long successfulPayments,
            long failedPayments,
            long pendingPayments
    ) {
        public double getSuccessRate() {
            return totalPayments > 0 ? (double) successfulPayments / totalPayments * 100 : 0;
        }
    }
}