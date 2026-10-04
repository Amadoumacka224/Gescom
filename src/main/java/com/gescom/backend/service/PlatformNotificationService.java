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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Historique des evenements importants de la plateforme.
 *
 * Meme regle que pour {@code ActivityLogService} : **si la notification echoue,
 * l'operation metier ne doit jamais echouer**. Perdre un paiement enregistre juste parce que
 * sa notification n'a pas pu etre ecrite, ce serait idiot. Les erreurs sont donc attrapees
 * et loggees en warning.
 *
 * {@code REQUIRES_NEW} va avec : sans sa propre transaction, une notification en erreur
 * marquerait la transaction de l'appelant en rollback-only, et l'operation echouerait quand
 * meme au commit (justement ce que le catch essaie d'eviter).
 */
@Service
@Transactional(readOnly = true)
public class PlatformNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformNotificationService.class);

    private final PlatformNotificationRepository notificationRepository;

    /**
     * Nouvelle transaction pour les ecritures faites apres le commit.
     *
     * Obligatoire, et pas juste pour faire comme le {@code REQUIRES_NEW} de {@link #record} :
     * dans un {@code afterCommit}, les ressources de la transaction sont encore liees au
     * thread alors que la transaction est deja terminee. Un {@code save} a ce moment-la
     * rejoint une transaction fermee et n'est jamais ecrit, sans aucune erreur.
     */
    private final TransactionTemplate afterCommitTransaction;

    public PlatformNotificationService(PlatformNotificationRepository notificationRepository,
                                       PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.afterCommitTransaction = new TransactionTemplate(transactionManager);
        this.afterCommitTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * Enregistre un evenement. Ne lance jamais d'exception.
     *
     * @param type     identifiant fixe du type d'evenement, traduit cote front
     * @param entity   nom de l'objet concerne, pour proposer un lien ; peut etre null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String type, PlatformNotification.Severity severity, String title,
                       String message, Company company, String entity, Long entityId) {
        write(type, severity, title, message, company, entity, entityId);
    }

    /**
     * Enregistre un evenement une fois la transaction de l'appelant terminee.
     *
     * A utiliser quand l'evenement concerne une entite creee dans cette meme transaction.
     * {@link #record} tourne en {@code REQUIRES_NEW}, donc sur une autre connexion : la ligne
     * n'y est pas encore visible, l'insertion ne respecte pas la cle etrangere, et l'echec du
     * commit remonte en {@code UnexpectedRollbackException} jusqu'a faire echouer l'operation
     * (justement ce que le catch devait empecher).
     *
     * En ecrivant apres le commit, on regle les deux : la ligne existe, et un echec de la
     * notification ne peut plus entrainer la transaction de l'appelant. S'il n'y a pas de
     * transaction en cours, ca fait la meme chose que {@link #record}.
     */
    public void recordAfterCommit(String type, PlatformNotification.Severity severity, String title,
                                  String message, Company company, String entity, Long entityId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(type, severity, title, message, company, entity, entityId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Le catch de write() ne suffit pas ici : il protege l'insertion, mais pas le
                // commit de cette transaction, qui arrive apres la lambda. Et Spring ne cache pas
                // ce qu'une synchronisation lance : l'exception remonterait au commit de
                // l'appelant et donnerait une 500 alors que tout est deja enregistre en base
                // (l'entreprise est creee, et le nouvel essai tomberait sur un email en double).
                try {
                    afterCommitTransaction.executeWithoutResult(status ->
                            write(type, severity, title, message, company, entity, entityId));
                } catch (Exception e) {
                    log.warn("Notification « {} » non enregistree apres commit : {}", type, e.getMessage());
                }
            }
        });
    }

    /** L'ecriture elle-meme. Ne lance jamais d'exception : mieux vaut perdre une trace qu'une operation. */
    private void write(String type, PlatformNotification.Severity severity, String title,
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
     * Coupe le texte a la taille de la colonne.
     *
     * Un texte trop long doit etre coupe plutot que faire echouer l'ecriture : la
     * notification est juste une trace, pas une donnee metier importante.
     */
    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
