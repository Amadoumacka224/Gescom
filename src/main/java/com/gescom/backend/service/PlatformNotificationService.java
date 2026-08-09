package com.gescom.backend.service;

import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.PlatformNotification;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.PlatformNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Journal des evenements notables de la plateforme.
 *
 * L'emission suit la meme regle que {@code ActivityLogService} : **une notification qui
 * echoue ne doit jamais faire echouer l'operation metier**. Un encaissement enregistre puis
 * perdu parce que sa notification n'a pas pu s'ecrire serait un tres mauvais echange. Les
 * erreurs sont donc avalees et tracees en avertissement.
 *
 * {@code REQUIRES_NEW} complete ce contrat : sans transaction propre, une notification
 * echouee marquerait la transaction appelante en rollback-only, et l'operation metier
 * echouerait quand meme au commit — exactement ce que le catch cherche a eviter.
 */
@Service
@Transactional(readOnly = true)
public class PlatformNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformNotificationService.class);

    private final PlatformNotificationRepository notificationRepository;

    public PlatformNotificationService(PlatformNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── Lectures ─────────────────────────────────────────────────────────────

    public Page<PlatformNotification> list(boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? notificationRepository.findByReadAtIsNullOrderByCreatedAtDesc(pageable)
                : notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public long countUnread() {
        return notificationRepository.countByReadAtIsNull();
    }

    // ── Ecritures ────────────────────────────────────────────────────────────

    @Transactional
    public PlatformNotification markRead(Long id) {
        PlatformNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return notification;
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(LocalDateTime.now());
    }

    /**
     * Consigne un evenement. Ne leve jamais.
     *
     * @param type     identifiant stable de la nature de l'evenement, traduit cote interface
     * @param entity   nom de l'objet vise, pour proposer un lien ; peut etre nul
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String type, PlatformNotification.Severity severity, String title,
                       String message, Company company, String entity, Long entityId) {
        try {
            PlatformNotification notification = new PlatformNotification();
            notification.setType(type);
            notification.setSeverity(severity);
            notification.setTitle(truncate(title, 150));
            notification.setMessage(truncate(message, 500));
            notification.setCompany(company);
            notification.setEntity(entity);
            notification.setEntityId(entityId);
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Notification « {} » non enregistree : {}", type, e.getMessage());
        }
    }

    /**
     * Tronque au format de la colonne.
     *
     * Un libelle trop long doit apparaitre ampute plutot que faire echouer l'ecriture : la
     * notification est une trace, pas une donnee metier dont l'integrite se defend.
     */
    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
