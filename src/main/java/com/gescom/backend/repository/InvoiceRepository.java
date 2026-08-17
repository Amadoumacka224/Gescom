package com.gescom.backend.repository;

import com.gescom.backend.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    /**
     * Toutes les factures avec la commande associée (client, créateur, lignes, produits) chargée
     * en une seule requête, pour éviter le N+1 au mapping — chaque InvoiceResponse embarque un
     * OrderResponse complet. DISTINCT à cause du JOIN FETCH sur la collection de lignes.
     *
     * {@code createdById} porte le cloisonnement caissier et s'entend du créateur de la VENTE,
     * pas de celui de la facture : une facture appartient au caissier qui a saisi la commande,
     * sans quoi il perdrait de vue la facture de sa propre vente dès qu'un collègue l'aurait
     * éditée. Nul, il désactive le filtre (vue ADMIN).
     */
    @Query("SELECT DISTINCT i FROM Invoice i " +
           "LEFT JOIN FETCH i.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy u " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product " +
           "WHERE (:createdById IS NULL OR u.id = :createdById) " +
           "ORDER BY i.invoiceDate DESC, i.id DESC")
    List<Invoice> findAllWithDetails(@Param("createdById") Long createdById);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Recherche d'une facture par son numéro, insensible à la casse et commande complète chargée.
     * Pendant de {@code OrderRepository.findByOrderNumberWithDetails} pour la saisie d'un retour :
     * le client donne indifféremment son numéro de commande ou celui de sa facture.
     */
    @Query("SELECT DISTINCT i FROM Invoice i " +
           "LEFT JOIN FETCH i.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE UPPER(i.invoiceNumber) = UPPER(:invoiceNumber)")
    Optional<Invoice> findByInvoiceNumberWithDetails(@Param("invoiceNumber") String invoiceNumber);
    Optional<Invoice> findByOrderId(Long orderId);
    List<Invoice> findByOrderIdIn(Collection<Long> orderIds);
    // Les trois listes filtrées portent le même `createdById` optionnel que findAllWithDetails :
    // aucune liste de factures ne doit offrir de contournement au cloisonnement caissier.
    @Query("SELECT i FROM Invoice i WHERE i.status = :status "
            + "AND (:createdById IS NULL OR i.order.createdBy.id = :createdById)")
    List<Invoice> findByStatus(@Param("status") Invoice.InvoiceStatus status,
                               @Param("createdById") Long createdById);

    @Query("SELECT i FROM Invoice i WHERE i.invoiceDate >= :start AND i.invoiceDate <= :end "
            + "AND (:createdById IS NULL OR i.order.createdBy.id = :createdById)")
    List<Invoice> findByInvoiceDateBetween(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end,
                                           @Param("createdById") Long createdById);

    @Query("SELECT i FROM Invoice i WHERE i.dueDate < :date AND i.status <> :status "
            + "AND (:createdById IS NULL OR i.order.createdBy.id = :createdById)")
    List<Invoice> findByDueDateBeforeAndStatusNot(@Param("date") LocalDate date,
                                                  @Param("status") Invoice.InvoiceStatus status,
                                                  @Param("createdById") Long createdById);

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

    /**
     * Recharge en un coup, avec tout leur detail, des factures deja selectionnees.
     *
     * Second temps de la recherche paginee, meme mecanique que
     * {@code OrderRepository.findAllWithDetailsByIds} et pour la meme raison : les requetes
     * ci-dessus embarquent un JOIN FETCH sur les lignes de la commande, et une requete qui
     * joint une collection ne peut pas etre paginee par la base — Hibernate rapatrierait tout
     * pour decouper en memoire (HHH90003004).
     */
    @Query("SELECT DISTINCT i FROM Invoice i " +
           "LEFT JOIN FETCH i.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE i.id IN :ids")
    List<Invoice> findAllWithDetailsByIds(@Param("ids") List<Long> ids);

    /**
     * Compteurs d'en-tete, agreges en base.
     *
     * `collected` et `pending` excluent les factures annulees : elles sont sorties des livres,
     * et compter leur encaissement sans compter leur reliquat donnerait un taux qui n'est celui
     * d'aucun perimetre. `overdue` n'est pas un statut mais une echeance depassee sur une
     * facture ni soldee ni annulee — d'ou le calcul plutot qu'un simple COUNT par statut.
     */
    @Query("""
           SELECT COUNT(i) AS total,
                  COALESCE(SUM(CASE WHEN i.status <> :canceled THEN i.paidAmount ELSE 0 END), 0) AS collected,
                  COALESCE(SUM(CASE WHEN i.status <> :canceled THEN i.totalAmount - i.paidAmount ELSE 0 END), 0) AS pending,
                  COALESCE(SUM(CASE WHEN i.dueDate < :today AND i.status <> :paid AND i.status <> :canceled THEN 1 ELSE 0 END), 0) AS overdue,
                  COALESCE(SUM(CASE WHEN i.status = :unpaid THEN 1 ELSE 0 END), 0) AS unpaid,
                  COALESCE(SUM(CASE WHEN i.status = :partial THEN 1 ELSE 0 END), 0) AS partial,
                  COALESCE(SUM(CASE WHEN i.status = :paid THEN 1 ELSE 0 END), 0) AS paid,
                  COALESCE(SUM(CASE WHEN i.status = :canceled THEN 1 ELSE 0 END), 0) AS canceled
           FROM Invoice i
           LEFT JOIN i.order o LEFT JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           """)
    InvoiceSummaryView summaryFor(@Param("createdById") Long createdById,
                                  @Param("today") LocalDate today,
                                  @Param("unpaid") Invoice.InvoiceStatus unpaid,
                                  @Param("partial") Invoice.InvoiceStatus partial,
                                  @Param("paid") Invoice.InvoiceStatus paid,
                                  @Param("canceled") Invoice.InvoiceStatus canceled);

    /** Clients ayant au moins une facture dans le perimetre de l'appelant. */
    @Query("""
           SELECT DISTINCT c.id AS id, c.firstName AS firstName, c.lastName AS lastName, c.company AS company
           FROM Invoice i JOIN i.order o JOIN o.client c LEFT JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           ORDER BY c.lastName, c.firstName
           """)
    List<InvoiceClientView> findDistinctClients(@Param("createdById") Long createdById);

    interface InvoiceClientView {
        Long getId();
        String getFirstName();
        String getLastName();
        String getCompany();
    }

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour le motif. */
    interface InvoiceSummaryView {
        long getTotal();
        BigDecimal getCollected();
        BigDecimal getPending();
        long getOverdue();
        long getUnpaid();
        long getPartial();
        long getPaid();
        long getCanceled();
    }
}
