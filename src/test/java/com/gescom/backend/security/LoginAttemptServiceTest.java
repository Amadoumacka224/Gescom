package com.gescom.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limite des tentatives de connexion.
 *
 * Le plus important n'est pas « ca bloque au bout de cinq », c'est evident, mais le choix de
 * la cle : le compteur porte sur le couple identifiant + adresse. Deux tests sont la pour ca,
 * parce qu'avec la mauvaise cle, la protection empecherait le vrai utilisateur de se connecter.
 *
 * Test unitaire simple : pas de contexte Spring, le composant est cree directement avec ses
 * reglages.
 */
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String OTHER_IP = "10.0.0.2";

    /** Trois essais, blocage d'une heure : les valeurs exactes ne comptent pas, c'est l'effet qu'on teste. */
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
     * Le point principal : un attaquant qui insiste sur le compte « admin » depuis sa machine
     * ne doit pas bloquer le vrai utilisateur, qui se connecte depuis un autre poste.
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
     * L'inverse du test precedent : plusieurs postes derriere la meme connexion internet (le
     * cas normal dans un magasin) ne partagent pas le meme compteur pour chaque compte.
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

        // Sans la remise a zero, ces quatre echecs a la suite auraient depasse la limite de trois.
        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    /** Blocage de zero seconde : il expire tout de suite, ce qui permet de tester la fin du blocage sans attendre. */
    @Test
    void le_verrou_expire_de_lui_meme() {
        LoginAttemptService service = service(3, 0);

        fail(service, "admin", IP, 3);

        assertThatCode(() -> service.checkAllowed("admin", IP)).doesNotThrowAnyException();
    }

    /**
     * Apres l'expiration, le compteur doit repartir de zero. S'il restait au max, le premier
     * essai suivant rebloquerait tout de suite, et l'utilisateur serait puni pour rien.
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

    /** Un identifiant inconnu ou vide ne doit pas faire planter le comptage. */
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
