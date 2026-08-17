package com.gescom.backend.repository;

import com.gescom.backend.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    /**
     * Toutes les livraisons avec la commande associée (client, créateur, lignes, produits) chargée
     * en une seule requête, pour éviter le N+1 au mapping — chaque DeliveryResponse embarque un
     * OrderResponse complet. DISTINCT à cause du JOIN FETCH sur la collection de lignes.
     *
     * {@code createdById} porte le cloisonnement caissier, rapporté au créateur de la VENTE :
     * une livraison suit la commande dont elle découle, pas l'opérateur qui l'a planifiée.
     * Nul, il désactive le filtre (vue ADMIN).
     */
    @Query("SELECT DISTINCT d FROM Delivery d " +
           "LEFT JOIN FETCH d.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy u " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product " +
           "WHERE (:createdById IS NULL OR u.id = :createdById) " +
           "ORDER BY d.id DESC")
    List<Delivery> findAllWithDetails(@Param("createdById") Long createdById);

    Optional<Delivery> findByDeliveryNumber(String deliveryNumber);
    Optional<Delivery> findByOrderId(Long orderId);

    // Mêmes listes filtrées, même `createdById` optionnel : aucune liste de livraisons ne doit
    // offrir de contournement au cloisonnement caissier.
    @Query("SELECT d FROM Delivery d WHERE d.status = :status "
            + "AND (:createdById IS NULL OR d.order.createdBy.id = :createdById)")
    List<Delivery> findByStatus(@Param("status") Delivery.DeliveryStatus status,
                                @Param("createdById") Long createdById);

    @Query("SELECT d FROM Delivery d WHERE d.scheduledDate >= :start AND d.scheduledDate <= :end "
            + "AND (:createdById IS NULL OR d.order.createdBy.id = :createdById)")
    List<Delivery> findByScheduledDateBetween(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("createdById") Long createdById);

    /**
     * Recharge en un coup, avec tout leur detail, des livraisons deja selectionnees.
     *
     * Second temps de la recherche paginee, meme mecanique que pour les commandes et les
     * factures : la requete ci-dessus embarque un JOIN FETCH sur les lignes de la commande, et
     * une requete qui joint une collection ne peut pas etre paginee par la base — Hibernate
     * rapatrierait tout pour decouper en memoire (HHH90003004).
     */
    @Query("SELECT DISTINCT d FROM Delivery d " +
           "LEFT JOIN FETCH d.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE d.id IN :ids")
    List<Delivery> findAllWithDetailsByIds(@Param("ids") List<Long> ids);

    /**
     * Compteurs d'en-tete, agreges en base.
     *
     * `late` n'est pas un statut mais une date prevue depassee sur une livraison ENCORE EN
     * ATTENTE : une livraison effectuee en retard n'est plus en retard, elle est faite. Le
     * compteur designe donc un sous-ensemble de `pending`, il ne s'y ajoute pas.
     */
    @Query("""
           SELECT COUNT(d) AS total,
                  COALESCE(SUM(CASE WHEN d.status = :pending THEN 1 ELSE 0 END), 0) AS pending,
                  COALESCE(SUM(CASE WHEN d.status = :delivered THEN 1 ELSE 0 END), 0) AS delivered,
                  COALESCE(SUM(CASE WHEN d.status = :pending AND d.scheduledDate < :startOfToday THEN 1 ELSE 0 END), 0) AS late
           FROM Delivery d
           LEFT JOIN d.order o LEFT JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           """)
    DeliverySummaryView summaryFor(@Param("createdById") Long createdById,
                                   @Param("startOfToday") LocalDateTime startOfToday,
                                   @Param("pending") Delivery.DeliveryStatus pending,
                                   @Param("delivered") Delivery.DeliveryStatus delivered);

    /** Clients ayant au moins une livraison dans le perimetre de l'appelant. */
    @Query("""
           SELECT DISTINCT c.id AS id, c.firstName AS firstName, c.lastName AS lastName, c.company AS company
           FROM Delivery d JOIN d.order o JOIN o.client c LEFT JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           ORDER BY c.lastName, c.firstName
           """)
    List<DeliveryClientView> findDistinctClients(@Param("createdById") Long createdById);

    @Query("""
           SELECT DISTINCT d.deliveryCity FROM Delivery d LEFT JOIN d.order o LEFT JOIN o.createdBy u
           WHERE d.deliveryCity IS NOT NULL AND d.deliveryCity <> ''
             AND (:createdById IS NULL OR u.id = :createdById)
           ORDER BY d.deliveryCity
           """)
    List<String> findDistinctCities(@Param("createdById") Long createdById);

    @Query("""
           SELECT DISTINCT d.deliveryCountry FROM Delivery d LEFT JOIN d.order o LEFT JOIN o.createdBy u
           WHERE d.deliveryCountry IS NOT NULL AND d.deliveryCountry <> ''
             AND (:createdById IS NULL OR u.id = :createdById)
           ORDER BY d.deliveryCountry
           """)
    List<String> findDistinctCountries(@Param("createdById") Long createdById);

    interface DeliveryClientView {
        Long getId();
        String getFirstName();
        String getLastName();
        String getCompany();
    }

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour le motif. */
    interface DeliverySummaryView {
        long getTotal();
        long getPending();
        long getDelivered();
        long getLate();
    }

    /**
     * Plus haut numero deja attribue pour ce prefixe et cette annee, ou null s'il n'y en a
     * aucun.
     *
     * Un MAX sur la CHAINE, valide parce que le compteur est complete a largeur fixe :
     * LIV-2026-0042 se compare bien avant LIV-2026-0100. Sans ce remplissage, l'ordre
     * lexicographique placerait 9 apres 10 et la suite repartirait en arriere.
     *
     * Les numeros de l'ancien format (LIV- suivi d'un horodatage) ne matchent pas le motif :
     * ils sont ignores, et les deux formes cohabitent sans se marcher dessus.
     *
     * Le cloisonnement par entreprise est assure par le filtre Hibernate, qui couvre les
     * requetes JPQL : chaque entreprise ne voit donc que ses propres numeros.
     */
    @Query("SELECT MAX(d.deliveryNumber) FROM Delivery d WHERE d.deliveryNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
