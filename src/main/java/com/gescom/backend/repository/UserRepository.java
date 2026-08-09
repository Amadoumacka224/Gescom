package com.gescom.backend.repository;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    // Variante pour la mise à jour : vrai si un AUTRE utilisateur (id différent) utilise déjà cet email.
    // (le username n'est pas modifiable après création, d'où l'absence de variante équivalente)
    Boolean existsByEmailAndIdNot(String email, Long id);
    List<User> findByRole(User.Role role);
    List<User> findByActive(Boolean active);
    List<User> findByRoleAndActive(User.Role role, Boolean active);

    // --- Vue plateforme -------------------------------------------------------
    // Comptages destinés au back-office propriétaire. Le filtre de cloisonnement étant
    // inactif pour le SUPER_ADMIN, ces méthodes portent bien sur l'ensemble du parc ;
    // appelées depuis une session d'entreprise, elles resteraient au contraire limitées
    // à celle-ci — ce qui est le comportement voulu dans les deux cas.

    long countByOwnerCompanyId(Long companyId);

    /**
     * Effectif du parc, propriétaire de la plateforme exclu : il n'est l'utilisateur d'aucune
     * entreprise cliente et n'a pas à gonfler le compteur.
     */
    long countByRoleNot(User.Role role);

    /**
     * Comptes actifs, même exclusion.
     *
     * L'exclusion doit être la même que celle de {@link #countByRoleNot} : les deux chiffres
     * s'affichent côte à côte sur le tableau de bord (« N actifs » sous le total), et un
     * périmètre différent produisait plus d'actifs que d'utilisateurs.
     */
    long countByActiveTrueAndRoleNot(User.Role role);

    /**
     * Recherche du parc pour le back-office propriétaire.
     *
     * L'entreprise est chargée dans la même requête : l'écran l'affiche à chaque ligne, ce
     * qui déclencherait sinon un N+1 par page — la relation est en EAGER, mais sur une
     * requête paginée Hibernate la résout ligne par ligne sans jointure explicite.
     */
    @Override
    @EntityGraph(attributePaths = "ownerCompany")
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    /**
     * Dernière connexion de chacun des utilisateurs indiqués.
     *
     * Agrégé en une seule requête pour toute la page plutôt qu'interrogé par ligne : c'est la
     * colonne la plus utile au support (« ce compte sert-il encore ? ») et la plus facile à
     * transformer en N+1. Renvoie des paires [userId, dernière date].
     */
    @Query("""
           SELECT al.user.id, MAX(al.createdAt) FROM ActivityLog al
           WHERE al.actionType = :loginAction
             AND al.user.id IN :userIds
           GROUP BY al.user.id
           """)
    List<Object[]> findLastLoginFor(List<Long> userIds, ActivityLog.ActionType loginAction);
}
