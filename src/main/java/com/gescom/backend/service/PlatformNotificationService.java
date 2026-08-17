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

    /**
     * Transaction neuve pour les ecritures differees apres commit.
     *
     * Indispensable, et pas seulement par symetrie avec le {@code REQUIRES_NEW} de
     * {@link #record} : dans un {@code afterCommit}, les ressources transactionnelles sont
     * encore liees au thread alors que la transaction est deja validee. Un {@code save} y
     * rejoint une transaction close et n'est jamais ecrit — sans la moindre erreur.
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
     * Consigne un evenement. Ne leve jamais.
     *
     * @param type     identifiant stable de la nature de l'evenement, traduit cote interface
     * @param entity   nom de l'objet vise, pour proposer un lien ; peut etre nul
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String type, PlatformNotification.Severity severity, String title,
                       String message, Company company, String entity, Long entityId) {
        write(type, severity, title, message, company, entity, entityId);
    }

    /**
     * Consigne un evenement une fois la transaction appelante validee.
     *
     * A reserver au cas ou l'evenement porte sur une entite creee dans cette meme
     * transaction. {@link #record} s'execute en {@code REQUIRES_NEW}, donc sur une autre
     * connexion : la ligne n'y est pas encore visible, l'insertion viole la cle etrangere, et
     * l'echec du commit interne remonte en {@code UnexpectedRollbackException} jusqu'a faire
     * echouer l'operation metier — precisement ce que le catch devait empecher.
     *
     * Differer l'ecriture apres le commit resout les deux : la ligne existe, et plus aucune
     * transaction appelante ne peut etre entrainee par un echec de la notification. Sans
     * transaction en cours, l'appel se comporte comme {@link #record}.
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
                // Le catch de write() ne suffit pas ici : il couvre l'insertion, pas le commit
                // de cette transaction-ci, qui a lieu apres le retour de la lambda. Or Spring
                // n'avale pas ce qu'une synchronisation leve — l'exception remonterait au
                // commit de l'appelant et ferait echouer en 500 une operation pourtant deja
                // validee en base, l'entreprise etant creee et le nouvel essai butant alors
                // sur un doublon d'email.
                try {
                    afterCommitTransaction.executeWithoutResult(status ->
                            write(type, severity, title, message, company, entity, entityId));
                } catch (Exception e) {
                    log.warn("Notification « {} » non enregistree apres commit : {}", type, e.getMessage());
                }
            }
        });
    }

    /** Ecriture proprement dite. Ne leve jamais : une trace perdue vaut mieux qu'une operation perdue. */
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
