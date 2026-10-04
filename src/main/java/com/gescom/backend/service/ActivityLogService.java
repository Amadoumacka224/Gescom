package com.gescom.backend.service;

import com.gescom.backend.dto.activity.ActivityFilterOptions;
import com.gescom.backend.dto.activity.ActivityLogSummary;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ActivityLogRepository;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service du journal d'activité.
 * Les autres services y enregistrent leurs opérations avec logActivity(), et il fournit
 * des recherches filtrées (par utilisateur, type d'action, entité, période) pour l'écran
 * Historique.
 */
@Service
@Transactional
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository, UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getAllActivities() {
        return activityLogRepository.findAllOrderByCreatedAtDesc();
    }

    /**
     * Une page du journal, filtrée avec les critères donnés (tous optionnels).
     * Le filtrage est fait en base et pas côté front : sur une liste paginée, filtrer les
     * lignes reçues ne donnerait que les résultats de la page en cours.
     */
    @Transactional(readOnly = true)
    public Page<ActivityLog> searchActivities(Long userId,
                                              ActivityLog.ActionType actionType,
                                              String entity,
                                              LocalDateTime start,
                                              LocalDateTime end,
                                              String search,
                                              Pageable pageable) {
        return activityLogRepository.findAll(
                buildFilter(null, false, userId, actionType, entity, start, end, search), pageable);
    }

    /**
     * Une page du journal de toutes les entreprises, pour le back-office.
     *
     * La différence avec {@link #searchActivities}, c'est le filtre sur l'entreprise, qui
     * n'a de sens qu'au niveau plateforme. `platformScope` fait l'inverse : il garde les
     * lignes sans entreprise, c'est-à-dire les actions du super admin (ses connexions par
     * exemple).
     */
    @Transactional(readOnly = true)
    public Page<ActivityLog> searchPlatformActivities(Long companyId,
                                                      boolean platformScope,
                                                      ActivityLog.ActionType actionType,
                                                      String entity,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      String search,
                                                      Pageable pageable) {
        return activityLogRepository.findAll(
                buildFilter(companyId, platformScope, null, actionType, entity, start, end, search),
                pageable);
    }

    /**
     * Lignes à exporter : tout le résultat filtré, pas seulement la page affichée. Un export
     * qui ne contient que les lignes visibles en faisant croire que c'est tout le résultat,
     * c'est le piège classique de ces écrans.
     *
     * Limité (`limit`) parce que le journal est la seule table qui grossit sans fin : sans
     * limite, un export sans filtre chargerait tout en mémoire. On garde les lignes les plus
     * récentes, ce sont celles qu'on veut voir.
     */
    @Transactional(readOnly = true)
    public List<ActivityLog> exportPlatformActivities(Long companyId,
                                                      boolean platformScope,
                                                      ActivityLog.ActionType actionType,
                                                      String entity,
                                                      LocalDateTime start,
                                                      LocalDateTime end,
                                                      String search,
                                                      int limit) {
        return activityLogRepository.findAll(
                buildFilter(companyId, platformScope, null, actionType, entity, start, end, search),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    /** Valeurs qui existent dans le journal : on ne propose pas un filtre qui ne donne rien. */
    @Transactional(readOnly = true)
    public ActivityFilterOptions getFilterOptions() {
        return new ActivityFilterOptions(
                activityLogRepository.findDistinctActionTypes().stream().map(Enum::name).toList(),
                activityLogRepository.findDistinctEntities());
    }

    private Specification<ActivityLog> buildFilter(Long companyId,
                                                   boolean platformScope,
                                                   Long userId,
                                                   ActivityLog.ActionType actionType,
                                                   String entity,
                                                   LocalDateTime start,
                                                   LocalDateTime end,
                                                   String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Filtre utilisé seulement par le back-office : comme le filtre par entreprise est
            // désactivé pour le SUPER_ADMIN, c'est le seul moyen de voir une seule entreprise.
            if (companyId != null) {
                predicates.add(cb.equal(root.get("ownerCompany").get("id"), companyId));
            } else if (platformScope) {
                predicates.add(cb.isNull(root.get("ownerCompany")));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (actionType != null) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            if (entity != null && !entity.isBlank()) {
                predicates.add(cb.equal(root.get("entity"), entity));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                // Mêmes champs que la recherche de l'écran Historique, avec une exception : le
                // nom traduit de l'action n'est pas en base (il est dans les fichiers i18n du
                // front), mais le type brut l'est via `entity` / description.
                var user = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("entity")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like),
                        cb.like(cb.lower(user.get("firstName")), like),
                        cb.like(cb.lower(user.get("lastName")), like),
                        cb.like(cb.lower(user.get("username")), like)));
            }
            // Aucun filtre : cb.and() sans argument est toujours vrai.
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getActivitiesByUser(Long userId, Pageable pageable) {
        return activityLogRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getActivitiesByActionType(ActivityLog.ActionType actionType, Pageable pageable) {
        return activityLogRepository.findByActionType(actionType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getActivitiesByEntity(String entity, Pageable pageable) {
        return activityLogRepository.findByEntity(entity, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getActivitiesByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return activityLogRepository.findByCreatedAtBetween(start, end, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> getCaissierActivities(Pageable pageable) {
        return activityLogRepository.findCaissierActivities(pageable);
    }

    /** Stats sur tout le journal, pour ne pas les calculer à partir de la page affichée. */
    @Transactional(readOnly = true)
    public ActivityLogSummary getSummary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        // Les 7 derniers jours, aujourd'hui compris, comme sur l'écran Historique.
        LocalDateTime startOfWeek = LocalDate.now().minusDays(6).atStartOfDay();

        return new ActivityLogSummary(
                activityLogRepository.count(),
                activityLogRepository.countByCreatedAtGreaterThanEqual(startOfToday),
                activityLogRepository.countByCreatedAtGreaterThanEqual(startOfWeek),
                activityLogRepository.countDistinctUsersSince(startOfToday),
                activityLogRepository.countByActionType(ActivityLog.ActionType.DELETE),
                activityLogRepository.findDistinctActionTypes(),
                activityLogRepository.findDistinctEntities());
    }

    @Transactional(readOnly = true)
    public Optional<ActivityLog> getActivityById(Long id) {
        return activityLogRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByUser(Long userId) {
        return activityLogRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByActionType(ActivityLog.ActionType actionType) {
        return activityLogRepository.findByActionType(actionType);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByEntity(String entity) {
        return activityLogRepository.findByEntity(entity);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByDateRange(LocalDateTime start, LocalDateTime end) {
        return activityLogRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getCaissierActivities() {
        return activityLogRepository.findCaissierActivities();
    }

    /** Crée et enregistre une entrée du journal, liée à l'utilisateur qui a fait l'action. */
    public ActivityLog logActivity(Long userId, ActivityLog.ActionType actionType, String entity, Long entityId, String description, String details, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId));

        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setActionType(actionType);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setDetails(details);
        log.setIpAddress(ipAddress);
        // On prend l'entreprise de l'auteur au lieu de laisser faire le TenantEntityListener,
        // qui ne trouve rien quand le contexte est vide. C'est le cas à la connexion : elle
        // est enregistrée avant qu'il y ait un token, et l'entrée LOGIN se retrouvait avec
        // company_id NULL, donc invisible dans l'écran Historique de l'entreprise (qui
        // exclut les NULL).
        //
        // Reste null pour le SUPER_ADMIN, qui n'a pas d'entreprise : c'est justement pour ça
        // que c'est la seule colonne company_id qui peut être vide.
        log.setOwnerCompany(user.getOwnerCompany());

        return activityLogRepository.save(log);
    }

    // Pas de suppression : on ne fait qu'ajouter dans le journal. Voir ActivityLogController.
}
