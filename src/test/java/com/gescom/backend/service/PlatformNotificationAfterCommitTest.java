package com.gescom.backend.service;

import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.PlatformNotification;
import com.gescom.backend.repository.PlatformNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quand une notification porte sur une entreprise creee a l'instant.
 *
 * L'ouverture d'un compte client cree l'entreprise et sa notification dans la meme
 * transaction. Ecrite pendant celle-ci, la notification part sur une autre connexion — le
 * {@code REQUIRES_NEW} de {@code record} — d'ou la ligne {@code companies} n'est pas encore
 * visible : la cle etrangere saute, et l'echec du commit interne remonte en
 * {@code UnexpectedRollbackException} jusqu'a faire echouer l'ouverture du compte. Le compte
 * n'etait donc pas cree, alors que la notification n'est qu'une trace.
 *
 * Deux exigences en decoulent, et la seconde est celle qu'on oublie : l'ecriture doit etre
 * differee apres le commit, <b>et</b> s'y faire dans une transaction neuve. Dans un
 * {@code afterCommit}, les ressources transactionnelles sont encore liees au thread alors que
 * la transaction est close ; un {@code save} qui les rejoint n'est jamais ecrit, et pas la
 * moindre erreur ne le signale.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformNotificationAfterCommitTest {

    @Mock private PlatformNotificationRepository notificationRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private PlatformNotificationService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new PlatformNotificationService(notificationRepository, transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        company = new Company();
        company.setId(7L);
        company.setName("Quincaillerie Dupont");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void record() {
        service.recordAfterCommit("COMPANY_PROVISIONED", PlatformNotification.Severity.INFO,
                "Nouveau client : " + company.getName(), "Compte ouvert",
                company, "Company", company.getId());
    }

    @Test
    void rienNEstEcritTantQueLaTransactionAppelanteCourt() {
        TransactionSynchronizationManager.initSynchronization();

        record();

        // Le point du correctif : ecrire ici viserait une entreprise que l'autre connexion
        // ne voit pas encore.
        verify(notificationRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void lEcritureALieuApresLeCommit() {
        TransactionSynchronizationManager.initSynchronization();
        record();

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<PlatformNotification> captor =
                ArgumentCaptor.forClass(PlatformNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("COMPANY_PROVISIONED");
        assertThat(captor.getValue().getCompany()).isSameAs(company);
    }

    /**
     * Et cette ecriture differee doit ouvrir sa propre transaction : sans quoi elle rejoint
     * celle qui vient d'etre validee et se perd en silence.
     */
    @Test
    void lEcritureDiffereeOuvreUneTransactionNeuve() {
        TransactionSynchronizationManager.initSynchronization();
        record();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<TransactionDefinition> captor =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(captor.capture());
        assertThat(captor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Hors transaction, il n'y a rien a differer : l'ecriture se fait tout de suite. */
    @Test
    void horsTransactionLEcritureEstImmediate() {
        record();

        verify(notificationRepository).save(any(PlatformNotification.class));
    }

    /**
     * Un echec au commit de l'ecriture differee reste sans consequence.
     *
     * C'est le dernier maillon du contrat « une notification ne fait jamais echouer une
     * operation metier », et le plus facile a manquer : le {@code try} autour du {@code save}
     * ne couvre pas le commit, qui a lieu apres. Spring ne rattrape pas ce qu'une
     * synchronisation leve — l'exception remonterait au commit de l'appelant et rendrait un
     * 500 sur une entreprise pourtant bel et bien creee.
     */
    @Test
    void unEchecAuCommitDiffereNeRemontePasALAppelant() {
        when(transactionManager.getTransaction(any()))
                .thenThrow(new CannotCreateTransactionException("connexion perdue"));
        TransactionSynchronizationManager.initSynchronization();
        record();

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
    }
}
