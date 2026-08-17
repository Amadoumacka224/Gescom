package com.gescom.backend.repository;

import com.gescom.backend.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * Plan de chargement commun à toutes les listes de commandes : client, créateur, lignes,
     * produits et catégories en une seule requête. La catégorie fait partie du lot parce que
     * `Product.category` est en EAGER — hors du fetch join, Hibernate la recharge séparément.
     * DISTINCT car le LEFT JOIN FETCH sur la collection de lignes duplique les commandes.
     *
     * Client et créateur sont aliasés pour que les filtres ci-dessous s'appuient sur la jointure
     * déjà présente, au lieu d'en déclencher une seconde par navigation implicite (o.client.id).
     */
    String WITH_DETAILS = "SELECT DISTINCT o FROM Order o "
            + "LEFT JOIN FETCH o.client c "
            + "LEFT JOIN FETCH o.createdBy u "
            + "LEFT JOIN FETCH o.items i "
            + "LEFT JOIN FETCH i.product p "
            + "LEFT JOIN FETCH p.category ";

    /**
     * Toutes les commandes visibles par la requête. {@code createdById} porte le cloisonnement
     * caissier : nul, il désactive le filtre (vue ADMIN) ; renseigné, il borne la liste aux
     * ventes de cet opérateur. Le filtre est appliqué en base et non après coup, faute de quoi
     * il ne tiendrait pas dès qu'une pagination ou une agrégation SQL viendrait s'y greffer.
     */
    @Query(WITH_DETAILS + "WHERE (:createdById IS NULL OR u.id = :createdById) ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails(@Param("createdById") Long createdById);

    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Recherche d'une vente par son numéro, insensible à la casse et lignes déjà chargées.
     * Sert la saisie d'un retour : l'utilisateur tape un numéro relevé sur un ticket, pas une
     * clé exacte, et le module a besoin des lignes pour calculer les quantités retournables.
     */
    @Query(WITH_DETAILS + "WHERE UPPER(o.orderNumber) = UPPER(:orderNumber)")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    // Les quatre listes filtrées reprennent le même plan de chargement : en requête dérivée,
    // chaque commande rechargeait ses lignes puis ses produits, soit ~2 requêtes par commande.
    // Elles portent toutes le même `createdById` optionnel que findAllWithDetails : aucune
    // liste de commandes ne doit offrir de contournement au cloisonnement caissier.
    @Query(WITH_DETAILS + "WHERE c.id = :clientId "
            + "AND (:createdById IS NULL OR u.id = :createdById) ORDER BY o.createdAt DESC")
    List<Order> findByClientId(@Param("clientId") Long clientId,
                               @Param("createdById") Long createdById);

    @Query(WITH_DETAILS + "WHERE u.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findByCreatedById(@Param("userId") Long userId);

    @Query(WITH_DETAILS + "WHERE o.status = :status "
            + "AND (:createdById IS NULL OR u.id = :createdById) ORDER BY o.createdAt DESC")
    List<Order> findByStatus(@Param("status") Order.OrderStatus status,
                             @Param("createdById") Long createdById);

    @Query(WITH_DETAILS + "WHERE o.createdAt >= :start AND o.createdAt <= :end "
            + "AND (:createdById IS NULL OR u.id = :createdById) ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("createdById") Long createdById);

    /**
     * Commandes d'un caissier sur une journée [start, end), lignes et client chargés
     * en une seule requête (évite le N+1 lors de l'agrégation du tableau de bord caisse).
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.client " +
           "WHERE o.createdBy.id = :userId " +
           "AND o.createdAt >= :start AND o.createdAt < :end " +
           "ORDER BY o.createdAt DESC")
    List<Order> findDayOrdersForCashier(@Param("userId") Long userId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Commandes de tous les caissiers sur une journée [start, end), lignes, client et créateur
     * chargés en une seule requête. Pendant « supervision des caisses » de
     * {@link #findDayOrdersForCashier} : évite de rapatrier toute la table pour n'en agréger
     * qu'un jour.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "WHERE o.createdAt >= :start AND o.createdAt < :end " +
           "ORDER BY o.createdAt DESC")
    List<Order> findDayOrders(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * Lecture de la commande avec verrou pessimiste, pendant de
     * {@code ProductRepository.findByIdForUpdate} au niveau de la vente.
     *
     * Sert les opérations dont le contrôle porte sur le cumul des lignes rattachées à la
     * commande — le retour client, dont la quantité retournable est « vendu − déjà rendu ».
     * Verrouiller les produits n'y suffit pas : deux retours simultanés liraient le même
     * « déjà rendu » avant de verrouiller quoi que ce soit, et passeraient tous deux.
     * Doit être appelé dans un contexte transactionnel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /**
     * Recharge en un coup, avec tout leur détail, des commandes déjà sélectionnées.
     *
     * Second temps de la recherche paginée. {@link #WITH_DETAILS} ne peut PAS être paginé
     * directement : dès qu'une requête embarque un {@code JOIN FETCH} sur une collection,
     * Hibernate ne sait plus traduire {@code LIMIT} en SQL — la jointure multiplie les lignes,
     * et découper le résultat brut couperait une commande au milieu de ses articles. Il
     * rapatrie alors TOUT et pagine en mémoire, en le signalant par l'avertissement
     * {@code HHH90003004}. La pagination serait purement décorative, et le remède pire que le
     * mal : même consommation qu'avant, plus le coût de la découpe.
     *
     * D'où la sélection des identifiants d'abord, sans jointure de collection donc paginée
     * pour de vrai par la base, puis ce chargement du détail sur ces seuls identifiants.
     *
     * L'ordre du résultat n'est pas garanti et n'a pas à l'être : l'appelant réordonne selon la
     * page qu'il a demandée.
     */
    @Query(WITH_DETAILS + "WHERE o.id IN :ids")
    List<Order> findAllWithDetailsByIds(@Param("ids") List<Long> ids);

    /**
     * Décompte par statut, agrégé en base, pour les tuiles de l'écran.
     *
     * {@code createdById} porte le cloisonnement caissier, comme dans {@link #findAllWithDetails} :
     * les tuiles doivent décrire le même ensemble que la liste, sans quoi un caissier lirait le
     * total de l'entreprise au-dessus de ses seules ventes.
     */
    // Statuts passés en paramètres plutôt qu'écrits en litéraux : un littéral énuméré qualifié
    // (`com.gescom...Order.OrderStatus.PENDING`) n'est pas interprété par Hibernate 6 et fait
    // échouer le démarrage sur « Could not interpret path expression ».
    @Query("""
           SELECT COUNT(o) AS total,
                  SUM(CASE WHEN o.status = :pending THEN 1 ELSE 0 END) AS pending,
                  SUM(CASE WHEN o.status = :confirmed THEN 1 ELSE 0 END) AS confirmed,
                  SUM(CASE WHEN o.status = :invoiced THEN 1 ELSE 0 END) AS invoiced,
                  SUM(CASE WHEN o.status = :delivered THEN 1 ELSE 0 END) AS delivered,
                  SUM(CASE WHEN o.status = :canceled THEN 1 ELSE 0 END) AS canceled
           FROM Order o
           LEFT JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           """)
    OrderSummaryView summaryFor(@Param("createdById") Long createdById,
                                @Param("pending") Order.OrderStatus pending,
                                @Param("confirmed") Order.OrderStatus confirmed,
                                @Param("invoiced") Order.OrderStatus invoiced,
                                @Param("delivered") Order.OrderStatus delivered,
                                @Param("canceled") Order.OrderStatus canceled);

    /**
     * Opérateurs ayant réellement saisi une vente dans le périmètre de l'appelant.
     *
     * Endpoint distinct de {@code /users}, et ce n'est pas un doublon : lister les utilisateurs
     * est réservé à l'ADMIN, alors que le filtre « saisie par » doit rester utilisable par un
     * caissier. Ici, {@code createdById} le ramène de toute façon à lui seul — le filtre lui
     * proposera une seule valeur, la sienne, et l'écran le masque dans ce cas.
     */
    @Query("""
           SELECT DISTINCT u.id AS id, u.firstName AS firstName, u.lastName AS lastName, u.username AS username
           FROM Order o JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           ORDER BY u.lastName, u.firstName
           """)
    List<OperatorView> findDistinctOperators(@Param("createdById") Long createdById);

    /** Villes des clients ayant commandé, pour la liste déroulante du filtre. */
    @Query("""
           SELECT DISTINCT c.city
           FROM Order o JOIN o.client c LEFT JOIN o.createdBy u
           WHERE c.city IS NOT NULL AND c.city <> ''
             AND (:createdById IS NULL OR u.id = :createdById)
           ORDER BY c.city
           """)
    List<String> findDistinctClientCities(@Param("createdById") Long createdById);

    interface OperatorView {
        Long getId();
        String getFirstName();
        String getLastName();
        String getUsername();
    }

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour le motif. */
    interface OrderSummaryView {
        long getTotal();
        long getPending();
        long getConfirmed();
        long getInvoiced();
        long getDelivered();
        long getCanceled();
    }

    /** Volume de commandes d'une entreprise — indicateur d'usage du back-office propriétaire. */
    long countByOwnerCompanyId(Long companyId);
}
