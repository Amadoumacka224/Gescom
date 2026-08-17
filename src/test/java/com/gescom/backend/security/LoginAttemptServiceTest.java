package com.gescom.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limitation des tentatives de connexion.
 *
 * Le point sensible n'est pas « ca bloque au bout de cinq », qui va de soi, mais le choix de la
 * cle : le compteur porte sur le couple identifiant + adresse. Deux tests s'y consacrent, parce
 * que se tromper de cle transforme la protection en deni de service contre l'utilisateur
 * legitime.
 *
 * Test unitaire pur : aucun contexte Spring, le composant est instancie avec ses reglages.
 */
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String OTHER_IP = "10.0.0.2";

    /** Trois essais, verrou d'une heure : les valeurs exactes n'importent pas, leur effet si. */
    private LoginAttemptService service(int maxAttempts, long lockSeconds) {
        return new LoginAttemptService(maxAttempts, lockSeconds);
    }

    private void fail(LoginAttemptService service, String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username, ip);
        }
    }

    @Test
    void laisse_passer_tant_que_le_seuil_n_est_pas_atteint() {
        LoginAttemptService service = service(3, 3600);

        fail(service, "admin", IP, 2);

        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    @Test
    void verrouille_au_seuil() {
        LoginAttemptService service = service(3, 3600);

        fail(service, "admin", IP, 3);

        assertThatThrownBy(() -> service.checkAllowed("admin", IP))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    /**
     * Le coeur du choix de conception : un attaquant qui martele le compte « admin » depuis sa
     * machine ne doit pas en fermer l'acces a son titulaire, qui se connecte d'ailleurs.
     */
    @Test
    void ne_verrouille_pas_le_meme_compte_depuis_une_autre_adresse() {
        LoginAttemptService service = service(3, 3600);

        fail(service, "admin", IP, 5);

        assertThatThrownBy(() -> service.checkAllowed("admin", IP))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> service.checkAllowed("admin", OTHER_IP)).doesNotThrowAnyException();
    }

    /**
     * Symetrique du precedent : plusieurs postes derriere une meme sortie internet — le cas
     * ordinaire d'un commerce — ne partagent pas leur quota compte par compte.
     */
    @Test
    void ne_verrouille_pas_un_autre_compte_depuis_la_meme_adresse() {
        LoginAttemptService service = service(3, 3600);

        fail(service, "caissier1", IP, 5);

        assertThatThrownBy(() -> service.checkAllowed("caissier1", IP))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    @Test
    void une_connexion_reussie_efface_les_echecs_precedents() {
        LoginAttemptService service = service(3, 3600);

        fail(service, "admin", IP, 2);
        service.recordSuccess("admin", IP);
        fail(service, "admin", IP, 2);

        // Sans la remise a zero, ces quatre echecs cumules auraient depasse le seuil de trois.
        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    /** Verrou de zero seconde : expire immediatement, ce qui teste la levee sans attendre. */
    @Test
    void le_verrou_expire_de_lui_meme() {
        LoginAttemptService service = service(3, 0);

        fail(service, "admin", IP, 3);

        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    /**
     * Apres expiration, le compteur doit repartir de zero. S'il restait au maximum, le tout
     * premier essai suivant reverrouillerait aussitot — l'utilisateur serait puni sans faute.
     */
    @Test
    void le_compteur_repart_de_zero_apres_le_verrou() {
        LoginAttemptService service = service(3, 0);

        fail(service, "admin", IP, 3);
        service.recordFailure("admin", IP);

        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    @Test
    void la_casse_de_l_identifiant_ne_cree_pas_de_compteur_distinct() {
        LoginAttemptService service = service(3, 3600);

        service.recordFailure("Admin", IP);
        service.recordFailure("ADMIN", IP);
        service.recordFailure("admin", IP);

        assertThatThrownBy(() -> service.checkAllowed("admin", IP))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void le_delai_restant_est_communique_a_l_appelant() {
        LoginAttemptService service = service(1, 600);

        service.recordFailure("admin", IP);

        assertThatThrownBy(() -> service.checkAllowed("admin", IP))
                .isInstanceOfSatisfying(TooManyAttemptsException.class, ex -> {
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 600L);
                    assertThat(ex.getMessageKey()).isEqualTo("auth.tooManyAttempts");
                });
    }

    /** Un identifiant inconnu ou vide ne doit pas faire echouer le comptage. */
    @Test
    void tolere_un_identifiant_ou_une_adresse_absents() {
        LoginAttemptService service = service(2, 3600);

        assertThatCode(() -> {
            service.recordFailure(null, null);
            service.recordFailure(null, null);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.checkAllowed(null, null))
                .isInstanceOf(TooManyAttemptsException.class);
    }
}
