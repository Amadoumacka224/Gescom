package com.gescom.backend.repository;

import com.gescom.backend.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * Chargement commun à toutes les listes de commandes : client, créateur, lignes, produits
     * et catégories en une seule requête. On inclut la catégorie parce que `Product.category`
     * est en EAGER : sans le fetch join, Hibernate la recharge à part.
     * DISTINCT car le LEFT JOIN FETCH sur les lignes duplique les commandes.
     *
     * Client et créateur ont un alias pour que les filtres plus bas utilisent la jointure déjà
     * faite, au lieu d'en refaire une avec o.client.id.
     */
    String WITH_DETAILS = "SELECT DISTINCT o FROM Order o "
            + "LEFT JOIN FETCH o.client c "
            + "LEFT JOIN FETCH o.createdBy u "
            + "LEFT JOIN FETCH o.items i "
            + "LEFT JOIN FETCH i.product p "
            + "LEFT JOIN FETCH p.category ";

    /**
     * Toutes les commandes visibles. {@code createdById} limite un caissier à ses ventes :
     * si null, pas de filtre (vue ADMIN) ; sinon, seulement les ventes de cet utilisateur. Le
     * filtre est fait en base et pas après coup, sinon il ne marcherait plus dès qu'on
     * ajouterait une pagination ou un calcul en SQL.
     */
    @Query(WITH_DETAILS + "WHERE (:createdById IS NULL OR u.id = :createdById) ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails(@Param("createdById") Long createdById);

    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Cherche une vente par son numéro (sans tenir compte de la casse), avec ses lignes.
     * Utilisé pour les retours : l'utilisateur tape un numéro lu sur un ticket, et on a besoin
     * des lignes pour calculer ce qui peut être retourné.
     */
    @Query(WITH_DETAILS + "WHERE UPPER(o.orderNumber) = UPPER(:orderNumber)")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    // Les quatre listes filtrées utilisent le même chargement : avec une requête dérivée,
    // chaque commande rechargeait ses lignes puis ses produits, environ 2 requêtes par commande.
    // Elles ont toutes le même `createdById` optionnel que findAllWithDetails : aucune liste
    // de commandes ne doit permettre à un caissier de voir les ventes des autres.
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
     * Commandes d'un caissier sur une journée [start, end), avec lignes et client en une seule
     * requête (évite le N+1 dans les calculs du tableau de bord caisse).
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
     * Commandes de tous les caissiers sur une journée [start, end), avec lignes, client et
     * créateur en une seule requête. C'est la version « suivi des caisses » de
     * {@link #findDayOrdersForCashier} : on évite de charger toute la table pour une journée.
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
     * Lit la commande avec un verrou pessimiste, comme
     * {@code ProductRepository.findByIdForUpdate} mais pour la vente.
     *
     * Sert quand on doit vérifier un total sur toutes les lignes de la commande : le retour
     * client, où la quantité retournable est « vendu − déjà rendu ». Verrouiller les produits
     * ne suffit pas : deux retours en même temps liraient le même « déjà rendu » avant de
     * verrouiller quoi que ce soit, et passeraient tous les deux.
     * À appeler dans une transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /**
     * Recharge d'un coup, avec tout le détail, des commandes déjà choisies.
     *
     * C'est la deuxième étape de la recherche paginée. On ne peut PAS paginer directement
     * {@link #WITH_DETAILS} : dès qu'une requête a un {@code JOIN FETCH} sur une collection,
     * Hibernate ne peut plus faire un {@code LIMIT} en SQL (la jointure multiplie les lignes,
     * et couper le résultat couperait une commande au milieu de ses articles). Il charge alors
     * TOUT et pagine en mémoire, avec le warning {@code HHH90003004}. La pagination ne
     * servirait à rien, et ce serait même pire qu'avant : même consommation, plus le découpage.
     *
     * Donc on sélectionne d'abord les ids, sans jointure sur la collection (donc vraiment
     * paginé en base), puis on charge le détail seulement pour ces ids.
     *
     * L'ordre du résultat n'est pas garanti, et pas besoin : l'appelant remet dans l'ordre de
     * la page demandée.
     */
    @Query(WITH_DETAILS + "WHERE o.id IN :ids")
    List<Order> findAllWithDetailsByIds(@Param("ids") List<Long> ids);

    /**
     * Nombre de commandes par statut, calculé en base, pour les cartes de l'écran.
     *
     * {@code createdById} limite un caissier à ses ventes, comme dans {@link #findAllWithDetails} :
     * les cartes doivent porter sur les mêmes commandes que la liste, sinon un caissier verrait
     * le total de toute l'entreprise au-dessus de ses propres ventes.
     */
    // Statuts passés en paramètres plutôt qu'écrits en dur : un enum écrit avec son nom complet
    // (`com.gescom...Order.OrderStatus.PENDING`) n'est pas compris par Hibernate 6, et le
    // démarrage plante avec « Could not interpret path expression ».
    @Query("""
           SELECT COUNT(o) AS total,
                  COALESCE(SUM(CASE WHEN o.status = :pending THEN 1 ELSE 0 END), 0) AS pending,
                  COALESCE(SUM(CASE WHEN o.status = :confirmed THEN 1 ELSE 0 END), 0) AS confirmed,
                  COALESCE(SUM(CASE WHEN o.status = :invoiced THEN 1 ELSE 0 END), 0) AS invoiced,
                  COALESCE(SUM(CASE WHEN o.status = :delivered THEN 1 ELSE 0 END), 0) AS delivered,
                  COALESCE(SUM(CASE WHEN o.status = :canceled THEN 1 ELSE 0 END), 0) AS canceled
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
     * Utilisateurs qui ont vraiment saisi une vente, parmi ce que l'appelant peut voir.
     *
     * Ce n'est pas un doublon de {@code /users} : lister les utilisateurs est réservé à
     * l'ADMIN, alors que le filtre « saisie par » doit marcher pour un caissier. Ici,
     * {@code createdById} le limite de toute façon à lui-même : il n'aura qu'une seule valeur,
     * la sienne, et l'écran cache le filtre dans ce cas.
     */
    @Query("""
           SELECT DISTINCT u.id AS id, u.firstName AS firstName, u.lastName AS lastName, u.username AS username
           FROM Order o JOIN o.createdBy u
           WHERE (:createdById IS NULL OR u.id = :createdById)
           ORDER BY u.lastName, u.firstName
           """)
    List<OperatorView> findDistinctOperators(@Param("createdById") Long createdById);

    /** Villes des clients qui ont commandé, pour la liste du filtre. */
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

    /** Projection par interface : voir {@code ProductRepository.CatalogSummaryView} pour la raison. */
    interface OrderSummaryView {
        long getTotal();
        long getPending();
        long getConfirmed();
        long getInvoiced();
        long getDelivered();
        long getCanceled();
    }

    /**
     * Commandes prêtes à livrer : facturées et sans livraison.
     *
     * Avant, l'écran Livraisons calculait cette liste lui-même, en enlevant des commandes
     * facturées celles qui étaient dans la liste des livraisons. Comme cette liste n'est plus
     * chargée en entier, on ne verrait que la page affichée et on proposerait de livrer une
     * commande déjà livrée (refusée ensuite par {@code createDelivery}, mais proposée).
     *
     * Avec le NOT EXISTS, c'est la base qui répond, et elle a toutes les données.
     */
    @Query(WITH_DETAILS + "WHERE o.status = :invoiced "
            + "AND NOT EXISTS (SELECT 1 FROM Delivery d WHERE d.order = o) "
            + "AND (:createdById IS NULL OR u.id = :createdById) "
            + "ORDER BY o.createdAt DESC")
    List<Order> findDeliverable(@Param("invoiced") Order.OrderStatus invoiced,
                                @Param("createdById") Long createdById);

    /**
     * Montant total des commandes, calculé en base.
     *
     * Les commandes ANNULÉES ne sont pas comptées : elles n'ont jamais été honorées. Ce sont
     * les mêmes commandes que le compteur « N commandes honorées » affiché juste à côté, sinon
     * le tableau de bord se contredirait.
     *
     * COALESCE sur la somme : SUM renvoie NULL et pas 0 quand il n'y a rien, et ce null
     * faisait planter tout le tableau de bord en 500.
     */
    @Query("SELECT COALESCE(SUM(o.finalAmount), 0) FROM Order o WHERE o.status <> :canceled")
    BigDecimal sumHonoredSales(@Param("canceled") Order.OrderStatus canceled);

    /**
     * Commandes facturées qui restent à planifier (ce qu'il reste à faire au magasin).
     *
     * Compté avec une vraie jointure et pas calculé avec « facturées − livraisons en
     * attente » : une livraison en attente peut rester après l'annulation de sa facture puis
     * de sa commande, et la soustraction donnerait un chiffre trop petit, voire négatif.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :invoiced "
            + "AND NOT EXISTS (SELECT 1 FROM Delivery d WHERE d.order = o)")
    long countToSchedule(@Param("invoiced") Order.OrderStatus invoiced);

    /** Dernières ventes, avec lignes et client déjà chargés pour le tableau de bord. */
    @Query(WITH_DETAILS + "ORDER BY o.createdAt DESC")
    List<Order> findRecent(Pageable pageable);

    /** Nombre de commandes d'une entreprise, pour le back-office du super admin. */
    long countByOwnerCompanyId(Long companyId);

    /**
     * Plus grand numero deja utilise pour ce prefixe et cette annee, ou null s'il n'y en a pas.
     *
     * Un MAX sur la CHAINE, ce qui marche parce que le compteur a toujours la meme longueur
     * (complete avec des zeros) : CMD-2026-0042 est bien avant CMD-2026-0100. Sans les zeros,
     * l'ordre alphabetique mettrait 9 apres 10 et la numerotation repartirait en arriere.
     *
     * Les anciens numeros (CMD- suivi d'un horodatage) ne correspondent pas au motif : ils
     * sont ignores, et les deux formats peuvent exister ensemble sans probleme.
     *
     * Le filtrage par entreprise est fait par le filtre Hibernate, qui s'applique aux
     * requetes JPQL : chaque entreprise ne voit que ses propres numeros.
     */
    @Query("SELECT MAX(o.orderNumber) FROM Order o WHERE o.orderNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
