package com.gescom.backend.repository;

import com.gescom.backend.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Toutes les factures avec la commande associée (client, créateur, lignes, produits) chargée
     * en une seule requête, pour éviter le N+1 au mapping — chaque InvoiceResponse embarque un
     * OrderResponse complet. DISTINCT à cause du JOIN FETCH sur la collection de lignes.
     */
    @Query("SELECT DISTINCT i FROM Invoice i " +
           "LEFT JOIN FETCH i.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product " +
           "ORDER BY i.invoiceDate DESC, i.id DESC")
    List<Invoice> findAllWithDetails();

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    Optional<Invoice> findByOrderId(Long orderId);
    List<Invoice> findByOrderIdIn(Collection<Long> orderIds);
    List<Invoice> findByStatus(Invoice.InvoiceStatus status);
    List<Invoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);
    List<Invoice> findByDueDateBeforeAndStatusNot(LocalDate date, Invoice.InvoiceStatus status);

    /**
     * Montant encaissé par un caissier à une date donnée.
     * NB : seules les factures soldées portent une paymentDate (cf. InvoiceService.recordPayment),
     * donc les paiements partiels — non datés dans le modèle actuel — ne sont pas comptés ici.
     */
    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.order.createdBy.id = :userId AND i.paymentDate = :date")
    BigDecimal sumCollectedByCashierOnDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Montant encaissé à une date donnée, ventilé par caissier : [userId, montant].
     * Même sémantique que {@link #sumCollectedByCashierOnDate} (seules les factures soldées
     * portent une paymentDate), mais en une requête pour toute la supervision.
     */
    @Query("SELECT i.order.createdBy.id, COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.paymentDate = :date AND i.order.createdBy IS NOT NULL " +
           "GROUP BY i.order.createdBy.id")
    List<Object[]> sumCollectedPerCashierOnDate(@Param("date") LocalDate date);
}
