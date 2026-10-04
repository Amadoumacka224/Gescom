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
     * Toutes les livraisons avec leur commande (client, créateur, lignes, produits) chargées en
     * une seule requête, pour éviter le N+1 au mapping (chaque DeliveryResponse contient un
     * OrderResponse complet). DISTINCT à cause du JOIN FETCH sur les lignes.
     *
     * {@code createdById} sert à limiter un caissier à ses ventes, en se basant sur le créateur
     * de la VENTE : une livraison suit sa commande, pas la personne qui l'a planifiée.
     * Si null, pas de filtre (vue ADMIN).
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

    // Même chose pour ces listes, avec le même `createdById` optionnel : aucune liste de
    // livraisons ne doit permettre à un caissier de voir les ventes des autres.
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
     * Recharge d'un coup, avec tout le detail, des livraisons deja choisies.
     *
     * C'est la deuxieme etape de la recherche paginee, comme pour les commandes et les
     * factures : la requete du dessus a un JOIN FETCH sur les lignes de la commande, et une
     * requete avec une jointure sur une collection ne peut pas etre paginee en base. Hibernate
     * chargerait tout et decouperait en memoire (HHH90003004).
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
     * Compteurs en haut de page, calcules en base.
     *
     * `late` n'est pas un statut : c'est une livraison ENCORE EN ATTENTE dont la date prevue
     * est passee (une livraison faite en retard n'est plus en retard, elle est faite). Ce
     * compteur fait donc partie de `pending`, il ne s'ajoute pas.
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

    /** Clients qui ont au moins une livraison visible par l'appelant. */
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

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour la raison. */
    interface DeliverySummaryView {
        long getTotal();
        long getPending();
        long getDelivered();
        long getLate();
    }

    /**
     * Plus grand numero deja utilise pour ce prefixe et cette annee, ou null s'il n'y en a pas.
     *
     * Un MAX sur la CHAINE, ce qui marche parce que le compteur a toujours la meme longueur
     * (complete avec des zeros) : LIV-2026-0042 est bien avant LIV-2026-0100. Sans les zeros,
     * l'ordre alphabetique mettrait 9 apres 10 et la numerotation repartirait en arriere.
     *
     * Les anciens numeros (LIV- suivi d'un horodatage) ne correspondent pas au motif : ils
     * sont ignores, et les deux formats peuvent exister ensemble sans probleme.
     *
     * Le filtrage par entreprise est fait par le filtre Hibernate, qui s'applique aux
     * requetes JPQL : chaque entreprise ne voit que ses propres numeros.
     */
    @Query("SELECT MAX(d.deliveryNumber) FROM Delivery d WHERE d.deliveryNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
