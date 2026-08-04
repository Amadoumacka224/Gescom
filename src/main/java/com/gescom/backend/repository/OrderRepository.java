package com.gescom.backend.repository;

import com.gescom.backend.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

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

    @Query(WITH_DETAILS + "ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

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
    @Query(WITH_DETAILS + "WHERE c.id = :clientId ORDER BY o.createdAt DESC")
    List<Order> findByClientId(@Param("clientId") Long clientId);

    @Query(WITH_DETAILS + "WHERE u.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findByCreatedById(@Param("userId") Long userId);

    @Query(WITH_DETAILS + "WHERE o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByStatus(@Param("status") Order.OrderStatus status);

    @Query(WITH_DETAILS + "WHERE o.createdAt >= :start AND o.createdAt <= :end ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

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
}
