package com.gescom.repository;

import com.gescom.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gescom.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InvoiceRepository  extends JpaRepository<Invoice, Long> {

    /**
     * Trouve une facture par numéro
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Trouve les factures par statut
     */
    List<Invoice> findByStatus(Invoice.InvoiceStatus status);

    /**
     * Trouve les factures par type
     */
    List<Invoice> findByInvoiceType(Invoice.InvoiceType invoiceType);

    // ===== STATISTIQUES FINANCIÈRES =====

    /**
     * CA total des factures payées depuis une date
     */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.invoiceDate >= :startDate AND i.status = 'PAID'")
    BigDecimal getTotalPaidRevenueFromDate(@Param("startDate") LocalDate startDate);

    /**
     * Montant total en attente de paiement
     */
    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i WHERE i.status IN (:statuses)")
    BigDecimal getTotalOutstandingAmount(@Param("statuses") List<Invoice.InvoiceStatus> statuses);


    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i WHERE i.dueDate < CURRENT_DATE AND i.status NOT IN (:excludedStatuses)")
    BigDecimal getTotalOverdueAmount(@Param("excludedStatuses") List<Invoice.InvoiceStatus> excludedStatuses);
    // ===== FACTURES EN RETARD =====

    /**
     * Factures échues
     */
    @Query("SELECT i FROM Invoice i WHERE i.dueDate < CURRENT_DATE AND i.status != 'PAID' AND i.status != 'CANCELLED' ORDER BY i.dueDate")
    List<Invoice> findOverdueInvoices();

    /**
     * Factures échues par commercial
     */
    @Query("SELECT i FROM Invoice i JOIN i.order o WHERE o.user = :user AND i.dueDate < CURRENT_DATE AND i.status != 'PAID' AND i.status != 'CANCELLED' ORDER BY i.dueDate")
    List<Invoice> findOverdueInvoicesByUser(@Param("user") User user);

    // ===== STATISTIQUES PAR PÉRIODE =====

    /**
     * CA facturé par mois
     */
    @Query("SELECT new map(" +
            "YEAR(i.invoiceDate) as year, " +
            "MONTH(i.invoiceDate) as month, " +
            "COALESCE(SUM(i.totalAmount), 0) as amount) " +
            "FROM Invoice i " +
            "WHERE i.invoiceDate >= :startDate " +
            "GROUP BY YEAR(i.invoiceDate), MONTH(i.invoiceDate) " +
            "ORDER BY YEAR(i.invoiceDate), MONTH(i.invoiceDate)")
    List<Map<String, Object>> getInvoiceAmountByMonth(@Param("startDate") LocalDate startDate);

    /**
     * Évolution des paiements par jour
     */
    @Query("SELECT new map(" +
            "DATE(i.paymentDate) as date, " +
            "COALESCE(SUM(i.paidAmount), 0) as amount) " +
            "FROM Invoice i " +
            "WHERE i.paymentDate >= :startDate AND i.paymentDate IS NOT NULL " +
            "GROUP BY DATE(i.paymentDate) " +
            "ORDER BY DATE(i.paymentDate)")
    List<Map<String, Object>> getPaymentsByDay(@Param("startDate") LocalDate startDate);

    // ===== STATISTIQUES PAR STATUT =====

    /**
     * Répartition des factures par statut
     */
    @Query("SELECT new map(" +
            "i.status as status, " +
            "COUNT(i) as count, " +
            "COALESCE(SUM(i.totalAmount), 0) as amount) " +
            "FROM Invoice i " +
            "WHERE i.invoiceDate >= :startDate " +
            "GROUP BY i.status")
    List<Map<String, Object>> getInvoiceStatsByStatus(@Param("startDate") LocalDate startDate);

    /**
     * Délai moyen de paiement
     */
    List<Invoice> findByInvoiceDateAfterAndPaymentDateIsNotNull(LocalDate startDate);




    // ===== STATISTIQUES PAR COMMERCIAL =====

    /**
     * CA facturé par commercial
     */
    @Query("SELECT new map(" +
            "CONCAT(u.firstName, ' ', u.lastName) as commercial, " +
            "COUNT(i) as invoiceCount, " +
            "COALESCE(SUM(i.totalAmount), 0) as totalAmount, " +
            "COALESCE(SUM(i.paidAmount), 0) as paidAmount) " +
            "FROM Invoice i JOIN i.order o JOIN o.user u " +
            "WHERE i.invoiceDate >= :startDate " +
            "GROUP BY u.id, u.firstName, u.lastName " +
            "ORDER BY SUM(i.totalAmount) DESC")
    List<Map<String, Object>> getInvoiceStatsByUser(@Param("startDate") LocalDate startDate);

    /**
     * Factures personnelles d'un commercial
     */
    @Query("SELECT i FROM Invoice i JOIN i.order o WHERE o.user = :user ORDER BY i.invoiceDate DESC")
    List<Invoice> findByUser(@Param("user") User user);

    // ===== MÉTHODES D'ANALYSE =====

    /**
     * Top clients par montant facturé
     */
    @Query("SELECT new map(" +
            "c.name as clientName, " +
            "COUNT(i) as invoiceCount, " +
            "COALESCE(SUM(i.totalAmount), 0) as totalAmount, " +
            "COALESCE(SUM(i.paidAmount), 0) as paidAmount) " +
            "FROM Invoice i JOIN i.order o JOIN o.client c " +
            "WHERE i.invoiceDate >= :startDate " +
            "GROUP BY c.id, c.name " +
            "ORDER BY SUM(i.totalAmount) DESC")
    List<Map<String, Object>> getTopClientsByInvoiceAmount(@Param("startDate") LocalDate startDate);

    /**
     * Analyse des retards de paiement par client
     */

    @Query("SELECT new map(" +
            "YEAR(i.invoiceDate) as year, " +
            "MONTH(i.invoiceDate) as month, " +
            "COUNT(i) as invoiceCount, " +
            "COALESCE(SUM(i.totalAmount), 0) as totalAmount, " +
            "COALESCE(SUM(CASE WHEN i.status = 'PAID' THEN i.totalAmount ELSE 0 END), 0) as paidAmount, " +
            "COALESCE(SUM(CASE WHEN i.status = 'OVERDUE' THEN i.totalAmount ELSE 0 END), 0) as overdueAmount) " +
            "FROM Invoice i " +
            "WHERE i.invoiceDate >= :startDate " +
            "GROUP BY YEAR(i.invoiceDate), MONTH(i.invoiceDate) " +
            "ORDER BY YEAR(i.invoiceDate), MONTH(i.invoiceDate)")
    List<Map<String, Object>> getMonthlyInvoiceSummary(@Param("startDate") LocalDate startDate);
}
