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
 * Liste des utilisateurs de toutes les entreprises, reservee au super admin.
 *
 * Ce service travaille sans filtre par entreprise : le contexte du SUPER_ADMIN est vide, le
 * filtre Hibernate n'est pas actif et les requetes portent sur toutes les entreprises. C'est
 * voulu, et c'est pour ca qu'on n'y accede que par {@code /api/platform/**}.
 *
 * Ce n'est pas un doublon de {@code UserService} : lui sert a l'ADMIN d'une entreprise pour
 * gerer ses comptes, celui-ci sert a surveiller toutes les entreprises. Ils n'ont ni le meme
 * perimetre ni les memes droits, et les melanger donnerait acces a l'un par l'autre.
 */
@Service
@Transactional(readOnly = true)
public class PlatformUserService {

    private final UserRepository userRepository;

    public PlatformUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Une page d'utilisateurs, avec filtres.
     *
     * Les filtres absents n'apparaissent pas dans la requete : elle est faite avec une
     * Specification et pas en JPQL avec des trous, comme {@code ActivityLogService.buildFilter},
     * car PostgreSQL gere mal un « :param IS NULL » sur un enum.
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
            // Se desactiver soi-meme bloquerait l'acces au back-office sans moyen de revenir :
            // le compte super admin ne se modifie par aucun ecran, seulement en base.
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
            // Le super admin n'appartient a aucune entreprise : il n'a rien a faire dans une
            // liste qui montre les utilisateurs des clients.
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
     * Charge les dernieres connexions de la page en une seule requete, puis cree le mapper.
     *
     * Chercher dans le journal pour chaque ligne serait le N+1 le plus facile a faire ici :
     * une page de vingt-cinq comptes ferait vingt-cinq requetes sur la plus grosse table de
     * la base.
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
