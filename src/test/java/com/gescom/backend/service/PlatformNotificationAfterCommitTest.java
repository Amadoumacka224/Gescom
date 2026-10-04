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
 * Cas d'une notification sur une entreprise qu'on vient de creer.
 *
 * Creer un compte client cree l'entreprise et sa notification dans la meme transaction. Si
 * on l'ecrit pendant cette transaction, la notification passe par une autre connexion (le
 * {@code REQUIRES_NEW} de {@code record}) qui ne voit pas encore la ligne {@code companies} :
 * la cle etrangere bloque, et l'echec du commit remonte en
 * {@code UnexpectedRollbackException} jusqu'a faire echouer la creation du compte. Le compte
 * n'etait donc pas cree, alors que la notification n'est qu'une trace.
 *
 * Il faut donc deux choses, et c'est la deuxieme qu'on oublie : ecrire apres le commit,
 * <b>et</b> le faire dans une nouvelle transaction. Dans un {@code afterCommit}, les
 * ressources de la transaction sont encore liees au thread alors qu'elle est terminee ; un
 * {@code save} qui les utilise n'est jamais ecrit, et aucune erreur ne le signale.
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

        // C'est tout l'interet de la correction : ecrire ici viserait une entreprise que
        // l'autre connexion ne voit pas encore.
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
     * Et cette ecriture apres le commit doit ouvrir sa propre transaction : sinon elle rejoint
     * celle qui vient de se terminer et est perdue sans rien dire.
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

    /** En dehors d'une transaction, rien a attendre : on ecrit tout de suite. */
    @Test
    void horsTransactionLEcritureEstImmediate() {
        record();

        verify(notificationRepository).save(any(PlatformNotification.class));
    }

    /**
     * Si le commit de l'ecriture differee echoue, ca ne doit avoir aucune consequence.
     *
     * C'est la derniere partie de la regle « une notification ne fait jamais echouer une
     * operation », et la plus facile a rater : le {@code try} autour du {@code save} ne couvre
     * pas le commit, qui arrive apres. Spring ne rattrape pas ce qu'une synchronisation lance :
     * l'exception remonterait au commit de l'appelant et donnerait une 500 alors que
     * l'entreprise est bien creee.
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
