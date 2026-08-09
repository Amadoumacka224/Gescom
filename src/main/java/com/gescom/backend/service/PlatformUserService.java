package com.gescom.backend.service;

import com.gescom.backend.dto.platform.PlatformUserResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vue consolidee des utilisateurs de tout le parc, reservee au proprietaire de la plateforme.
 *
 * Ce service travaille hors cloisonnement — le contexte du SUPER_ADMIN etant vide, le filtre
 * Hibernate reste inactif et les requetes traversent toutes les entreprises. C'est voulu, et
 * c'est pourquoi son unique porte d'entree est {@code /api/platform/**}.
 *
 * Il ne double pas {@code UserService} : celui-ci administre les comptes d'une entreprise
 * pour son propre ADMIN, celui-ci supervise le parc. Les deux ne partagent ni perimetre ni
 * droits, et les melanger reviendrait a ouvrir l'un par l'autre.
 */
@Service
@Transactional(readOnly = true)
public class PlatformUserService {

    private final UserRepository userRepository;

    public PlatformUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Page filtrable du parc.
     *
     * Les criteres absents ne participent pas a la requete — construite en Specification et
     * non en JPQL a trous, comme {@code ActivityLogService.buildFilter} : un « :param IS NULL »
     * sur un enum est mal digere par PostgreSQL.
     */
    public Page<PlatformUserResponse> search(Long companyId, String role, Boolean active,
                                             String search, Pageable pageable) {
        User.Role parsedRole = parseRole(role);
        Page<User> page = userRepository.findAll(filter(companyId, parsedRole, active, search), pageable);
        return page.map(withLastLogin(page.getContent()));
    }

    @Transactional
    public PlatformUserResponse setActive(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId));
        if (user.isPlatformOwner()) {
            // Se desactiver soi-meme fermerait le back-office sans aucun moyen de revenir :
            // le compte proprietaire n'est modifiable par aucun ecran, seulement en base.
            throw BusinessException.of("platform.user.ownerImmutable",
                    "Le compte proprietaire de la plateforme ne peut pas etre modifie ici");
        }
        user.setActive(active);
        User saved = userRepository.save(user);
        return toResponse(saved, null);
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private Specification<User> filter(Long companyId, User.Role role, Boolean active, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Le proprietaire de la plateforme n'est l'utilisateur d'aucune entreprise :
            // il n'a pas sa place dans une liste qui decrit le parc client.
            predicates.add(cb.notEqual(root.get("role"), User.Role.SUPER_ADMIN));

            if (companyId != null) {
                predicates.add(cb.equal(root.get("ownerCompany").get("id"), companyId));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("ownerCompany").get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Resout les dernieres connexions de la page en une requete, puis construit le mapper.
     *
     * Interroger le journal par ligne serait le N+1 le plus facile a introduire ici : une
     * page de vingt-cinq comptes vaudrait vingt-cinq requetes sur la table la plus volumineuse
     * de la base.
     */
    private java.util.function.Function<User, PlatformUserResponse> withLastLogin(List<User> users) {
        if (users.isEmpty()) {
            return user -> toResponse(user, null);
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, LocalDateTime> lastLogins = new HashMap<>();
        for (Object[] row : userRepository.findLastLoginFor(ids, ActivityLog.ActionType.LOGIN)) {
            lastLogins.put((Long) row[0], (LocalDateTime) row[1]);
        }
        return user -> toResponse(user, lastLogins.get(user.getId()));
    }

    private PlatformUserResponse toResponse(User user, LocalDateTime lastLoginAt) {
        Company company = user.getOwnerCompany();
        return new PlatformUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                user.getRole().name(),
                user.getActive(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                company != null ? company.getStatus().name() : null,
                lastLoginAt,
                user.getCreatedAt()
        );
    }

    private User.Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return User.Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.of("platform.user.role.invalid", "Role inconnu : " + value, value);
        }
    }
}
