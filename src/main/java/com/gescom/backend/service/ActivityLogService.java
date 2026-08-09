package com.gescom.backend.service;

import com.gescom.backend.dto.activity.ActivityLogSummary;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ActivityLogRepository;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service de journalisation des activités (piste d'audit).
 * Tous les autres services y déposent une trace de leurs opérations via logActivity(),
 * et il expose des requêtes filtrées (par utilisateur, type d'action, entité, période)
 * pour alimenter l'historique consulté dans l'interface d'administration.
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
     * Page du journal, filtrée sur les critères fournis (tous optionnels).
     * Le filtrage est fait en base et non côté client : sur une liste paginée, filtrer les
     * seules lignes reçues ne montrerait que les résultats présents dans la page courante.
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
                buildFilter(userId, actionType, entity, start, end, search), pageable);
    }

    private Specification<ActivityLog> buildFilter(Long userId,
                                                   ActivityLog.ActionType actionType,
                                                   String entity,
                                                   LocalDateTime start,
                                                   LocalDateTime end,
                                                   String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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
                // Même périmètre que la recherche de l'écran Historique, à une exception près :
                // le libellé traduit de l'action n'est pas interrogeable en base (il vit dans
                // les catalogues i18n du client), le type brut l'est via `entity`/description.
                var user = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("entity")), like),
                        cb.like(cb.lower(root.get("ipAddress")), like),
                        cb.like(cb.lower(user.get("firstName")), like),
                        cb.like(cb.lower(user.get("lastName")), like),
                        cb.like(cb.lower(user.get("username")), like)));
            }
            // Aucun critère : cb.and() sans argument est une conjonction toujours vraie.
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

    /** Indicateurs portant sur tout le journal, pour ne pas les déduire de la page affichée. */
    @Transactional(readOnly = true)
    public ActivityLogSummary getSummary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        // Fenêtre glissante de 7 jours, aujourd'hui inclus — cohérente avec l'écran Historique.
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

    /** Crée et persiste une entrée d'audit reliée à l'utilisateur qui a déclenché l'action. */
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
        // L'entreprise est déduite de l'auteur plutôt que laissée au TenantEntityListener,
        // qui n'a rien à déduire quand le contexte de cloisonnement est vide. C'est le cas
        // de la connexion : elle est journalisée avant qu'un jeton n'existe, et l'entrée
        // LOGIN se retrouvait alors avec company_id NULL — donc invisible sur l'écran
        // Historique de l'entreprise, dont le filtre exclut les NULL.
        //
        // Reste nul pour le SUPER_ADMIN, qui n'appartient à aucune entreprise : c'est
        // précisément pourquoi la colonne est la seule du cloisonnement à être facultative.
        log.setOwnerCompany(user.getOwnerCompany());

        return activityLogRepository.save(log);
    }

    // Pas de suppression : le journal est en ajout seul. Voir ActivityLogController.
}
