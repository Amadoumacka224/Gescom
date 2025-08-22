package com.gescom.repository;

import com.gescom.entity.ExternalPayment;
import com.gescom.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalPaymentRepository extends JpaRepository<ExternalPayment, Long> {

    /**
     * Trouve un paiement par son token de sécurité
     */
    Optional<ExternalPayment> findBySecurityToken(String securityToken);

    /**
     * Trouve un paiement par l'ID de transaction de la passerelle
     */
    Optional<ExternalPayment> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Trouve un paiement par l'ID de session de la passerelle
     */
    Optional<ExternalPayment> findByGatewaySessionId(String gatewaySessionId);

    /**
     * Trouve un paiement par l'ID de Payment Intent (Stripe)
     */
    Optional<ExternalPayment> findByGatewayPaymentIntentId(String gatewayPaymentIntentId);

    /**
     * Trouve tous les paiements pour une facture donnée
     */
    List<ExternalPayment> findByInvoiceOrderByCreatedAtDesc(Invoice invoice);

    /**
     * Trouve tous les paiements réussis pour une facture
     */
    @Query("SELECT ep FROM ExternalPayment ep WHERE ep.invoice = :invoice AND ep.status = 'SUCCEEDED'")
    List<ExternalPayment> findSuccessfulPaymentsByInvoice(@Param("invoice") Invoice invoice);

    /**
     * Trouve les paiements expirés qui doivent être nettoyés
     */
    @Query("SELECT ep FROM ExternalPayment ep WHERE ep.expiresAt < :now AND ep.status IN ('PENDING', 'PROCESSING')")
    List<ExternalPayment> findExpiredPayments(@Param("now") LocalDateTime now);

    /**
     * Trouve les paiements par statut
     */
    List<ExternalPayment> findByStatusOrderByCreatedAtDesc(ExternalPayment.PaymentStatus status);

    /**
     * Trouve les paiements par fournisseur de passerelle
     */
    List<ExternalPayment> findByGatewayProviderOrderByCreatedAtDesc(String gatewayProvider);

    /**
     * Compte les paiements par statut
     */
    long countByStatus(ExternalPayment.PaymentStatus status);

    /**
     * Trouve les paiements récents (dernières 24h)
     */
    @Query("SELECT ep FROM ExternalPayment ep WHERE ep.createdAt > :since ORDER BY ep.createdAt DESC")
    List<ExternalPayment> findRecentPayments(@Param("since") LocalDateTime since);

    /**
     * Vérifie s'il existe un paiement réussi pour une facture
     */
    @Query("SELECT COUNT(ep) > 0 FROM ExternalPayment ep WHERE ep.invoice = :invoice AND ep.status = 'SUCCEEDED'")
    boolean existsSuccessfulPaymentForInvoice(@Param("invoice") Invoice invoice);

    /**
     * Trouve les paiements suspects (même IP, nombreuses tentatives échouées)
     */
    @Query("SELECT ep FROM ExternalPayment ep WHERE ep.clientIp = :ip AND ep.status = 'FAILED' AND ep.createdAt > :since")
    List<ExternalPayment> findSuspiciousPaymentsByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);
}