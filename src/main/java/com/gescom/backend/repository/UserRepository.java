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
    // Version pour la modification : vrai si un AUTRE utilisateur (id différent) a déjà cet email.
    // (on ne peut pas changer le username après la création, donc pas besoin de la même chose pour lui)
    Boolean existsByEmailAndIdNot(String email, Long id);
    List<User> findByRole(User.Role role);
    List<User> findByActive(Boolean active);
    List<User> findByRoleAndActive(User.Role role, Boolean active);

    // --- Vue plateforme -------------------------------------------------------
    // Comptages pour le back-office du super admin. Comme le filtre par entreprise n'est pas
    // actif pour le SUPER_ADMIN, ces méthodes portent bien sur toutes les entreprises ;
    // appelées depuis une entreprise, elles resteraient limitées à celle-ci. Dans les deux
    // cas c'est ce qu'on veut.

    long countByOwnerCompanyId(Long companyId);

    /**
     * Nombre d'utilisateurs, sans le super admin : il n'appartient à aucune entreprise
     * cliente et ne doit pas être compté.
     */
    long countByRoleNot(User.Role role);

    /**
     * Comptes actifs, sans le super admin non plus.
     *
     * Il faut exclure la même chose que dans {@link #countByRoleNot} : les deux chiffres sont
     * affichés côte à côte sur le tableau de bord (« N actifs » sous le total), et avec une
     * règle différente on avait plus d'actifs que d'utilisateurs.
     */
    long countByActiveTrueAndRoleNot(User.Role role);

    /**
     * Recherche des utilisateurs pour le back-office du super admin.
     *
     * L'entreprise est chargée dans la même requête : l'écran l'affiche sur chaque ligne,
     * sinon on aurait un N+1 par page. La relation est en EAGER, mais sur une requête paginée
     * Hibernate la charge ligne par ligne s'il n'y a pas de jointure explicite.
     */
    @Override
    @EntityGraph(attributePaths = "ownerCompany")
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    /**
     * Dernière connexion de chaque utilisateur demandé.
     *
     * Une seule requête pour toute la page plutôt qu'une par ligne : c'est la colonne la plus
     * utile pour le support (« ce compte sert-il encore ? ») et la plus facile à transformer en
     * N+1. Renvoie des paires [userId, dernière date].
     */
    @Query("""
           SELECT al.user.id, MAX(al.createdAt) FROM ActivityLog al
           WHERE al.actionType = :loginAction
             AND al.user.id IN :userIds
           GROUP BY al.user.id
           """)
    List<Object[]> findLastLoginFor(List<Long> userIds, ActivityLog.ActionType loginAction);
}
