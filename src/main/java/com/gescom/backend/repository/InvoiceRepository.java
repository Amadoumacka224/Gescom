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
     * Toutes les factures avec leur commande (client, créateur, lignes, produits) chargées en
     * une seule requête, pour éviter le N+1 au mapping (chaque InvoiceResponse contient un
     * OrderResponse complet). DISTINCT à cause du JOIN FETCH sur les lignes.
     *
     * {@code createdById} limite un caissier à ses ventes, en se basant sur le créateur de la
     * VENTE et pas de la facture : une facture appartient au caissier qui a saisi la commande,
     * sinon il ne verrait plus la facture de sa propre vente dès qu'un collègue l'aurait
     * créée. Si null, pas de filtre (vue ADMIN).
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
     * Cherche une facture par son numéro (sans tenir compte de la casse), avec la commande
     * complète. C'est l'équivalent de {@code OrderRepository.findByOrderNumberWithDetails} pour
     * les retours : le client peut donner son numéro de commande ou celui de sa facture.
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
    // Les trois listes filtrées ont le même `createdById` optionnel que findAllWithDetails :
    // aucune liste de factures ne doit permettre à un caissier de voir les ventes des autres.
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
     * Attention : seules les factures entièrement payées ont une paymentDate (voir
     * InvoiceService.recordPayment), donc les paiements partiels (sans date pour l'instant)
     * ne sont pas comptés ici.
     */
    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.order.createdBy.id = :userId AND i.paymentDate = :date")
    BigDecimal sumCollectedByCashierOnDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Montant encaissé à une date donnée, par caissier : [userId, montant].
     * Même règle que {@link #sumCollectedByCashierOnDate} (seules les factures payées ont une
     * paymentDate), mais en une seule requête pour tout le suivi des caisses.
     */
    @Query("SELECT i.order.createdBy.id, COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.paymentDate = :date AND i.order.createdBy IS NOT NULL " +
           "GROUP BY i.order.createdBy.id")
    List<Object[]> sumCollectedPerCashierOnDate(@Param("date") LocalDate date);

    /**
     * Recharge d'un coup, avec tout le detail, des factures deja choisies.
     *
     * Deuxieme etape de la recherche paginee, comme
     * {@code OrderRepository.findAllWithDetailsByIds} et pour la meme raison : les requetes du
     * dessus ont un JOIN FETCH sur les lignes de la commande, et une requete avec une jointure
     * sur une collection ne peut pas etre paginee en base. Hibernate chargerait tout et
     * decouperait en memoire (HHH90003004).
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
     * Compteurs en haut de page, calcules en base.
     *
     * `collected` et `pending` ne comptent pas les factures annulees : elles ne comptent plus,
     * et compter ce qui a ete paye dessus sans compter ce qui reste donnerait un taux qui ne
     * veut rien dire. `overdue` n'est pas un statut mais une facture ni payee ni annulee dont
     * l'echeance est passee, d'ou le calcul au lieu d'un simple COUNT par statut.
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

    /** Clients qui ont au moins une facture visible par l'appelant. */
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

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour la raison. */
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

    /**
     * Plus grand numero deja utilise pour ce prefixe et cette annee, ou null s'il n'y en a pas.
     *
     * Un MAX sur la CHAINE, ce qui marche parce que le compteur a toujours la meme longueur
     * (complete avec des zeros) : FACT-2026-0042 est bien avant FACT-2026-0100. Sans les zeros,
     * l'ordre alphabetique mettrait 9 apres 10 et la numerotation repartirait en arriere.
     *
     * Les anciens numeros (FACT- suivi d'un horodatage) ne correspondent pas au motif : ils
     * sont ignores, et les deux formats peuvent exister ensemble sans probleme.
     *
     * Le filtrage par entreprise est fait par le filtre Hibernate, qui s'applique aux
     * requetes JPQL : chaque entreprise ne voit que ses propres numeros.
     */
    @Query("SELECT MAX(i.invoiceNumber) FROM Invoice i WHERE i.invoiceNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
