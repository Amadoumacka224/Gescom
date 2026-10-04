package com.gescom.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite le nombre de tentatives de connexion.
 *
 * {@code /api/auth/login} etait la seule route accessible sans token, et il n'y avait aucune
 * limite : pas de compteur d'echecs, pas de blocage, rien. Le cout de BCrypt (12 tours,
 * quelques centaines de millisecondes) ralentit un attaquant mais ne l'arrete pas : en une
 * nuit, il a largement le temps d'essayer les mots de passe courants sur un compte connu.
 *
 * <h2>Ce qu'on compte</h2>
 *
 * Le compteur porte sur le couple <b>identifiant + adresse IP</b>, et pas sur un seul des
 * deux. C'est le point important :
 *
 * <ul>
 *   <li><b>Avec l'identifiant seul</b>, n'importe qui pourrait bloquer le compte {@code admin}
 *       de l'exterieur en tapant de faux mots de passe : la protection servirait a empecher
 *       le vrai utilisateur de se connecter.</li>
 *   <li><b>Avec l'adresse seule</b>, tous les postes derriere la meme connexion internet (le
 *       cas normal dans un magasin) partageraient le meme compteur, et les erreurs de frappe
 *       d'un collegue bloqueraient la caisse d'a cote.</li>
 *   <li><b>Avec le couple</b>, un attaquant qui insiste sur un compte depuis une machine est
 *       bloque pour ce compte, mais le vrai utilisateur peut toujours se connecter depuis son
 *       poste.</li>
 * </ul>
 *
 * <h2>Ce que ca n'empeche pas</h2>
 *
 * Une attaque repartie sur beaucoup d'adresses passe quand meme, chaque couple restant sous
 * la limite. Pour ca il faudrait un pare-feu applicatif ou une limite en amont, pas un
 * compteur dans l'appli. C'est une limite connue, pas un oubli.
 *
 * <h2>En memoire</h2>
 *
 * Le compteur est dans l'instance : il repart a zero au redemarrage, et deux instances
 * derriere un load balancer comptent chacune de leur cote. Pour un outil interne ca suffit,
 * et ca evite d'ajouter Redis et une table pour une protection qui sert a ralentir, pas a
 * garder un historique. Pour passer a un compteur partage, il suffirait de remplacer la map
 * ci-dessous.
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Au-dela de cette taille, on supprime les entrees expirees. Ce n'est pas une regle
     * fonctionnelle, juste une protection pour la memoire : sans ca, une attaque qui change
     * d'identifiant a chaque essai ferait grossir la map sans fin.
     */
    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration lockDuration;

    /** Une entree par couple identifiant / adresse. */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.lock-duration-seconds:900}") long lockDurationSeconds) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofSeconds(lockDurationSeconds);
    }

    /**
     * Verifie qu'on a encore le droit d'essayer, sinon lance {@link TooManyAttemptsException}.
     *
     * A appeler AVANT de verifier le mot de passe : si on verifiait d'abord, l'attaquant
     * aurait quand meme droit au calcul BCrypt qu'on veut justement lui refuser.
     */
    public void checkAllowed(String username, String clientIp) {
        Attempt attempt = attempts.get(key(username, clientIp));
        if (attempt == null) {
            return;
        }
        long remaining = attempt.remainingLockSeconds();
        if (remaining > 0) {
            throw new TooManyAttemptsException(remaining);
        }
    }

    /**
     * Enregistre un echec. Au {@code maxAttempts}-ieme, le couple est bloque pendant
     * {@code lockDuration}.
     *
     * Le blocage s'arrete tout seul : pas besoin de debloquer a la main, et quelqu'un qui
     * s'est juste trompe peut se reconnecter sans appeler personne.
     */
    public void recordFailure(String username, String clientIp) {
        purgeIfCrowded();
        String key = key(username, clientIp);
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        int count = attempt.registerFailure(lockDuration, maxAttempts);
        if (count >= maxAttempts) {
            // Log en WARN : ca veut dire qu'un compte est vise. Le journal d'activite, lui,
            // n'enregistre que les connexions REUSSIES.
            log.warn("Connexion verrouillee pour « {} » depuis {} apres {} echecs",
                    username, clientIp, count);
        }
    }

    /** Remet le compteur a zero apres une connexion reussie : seuls les echecs a la suite comptent. */
    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    /**
     * Identifiant mis en minuscules : sinon, en alternant « Admin » et « admin », on aurait
     * un compteur different pour chaque variante.
     */
    private String key(String username, String clientIp) {
        return (username == null ? "" : username.toLowerCase()) + '|' + (clientIp == null ? "" : clientIp);
    }

    private void purgeIfCrowded() {
        if (attempts.size() < PURGE_THRESHOLD) {
            return;
        }
        attempts.values().removeIf(Attempt::isExpired);
    }

    /** Compteur d'un couple : nombre d'echecs a la suite et fin du blocage s'il y en a un. */
    private static final class Attempt {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile Instant lockedUntil = Instant.EPOCH;

        int registerFailure(Duration lockDuration, int maxAttempts) {
            int count = failures.incrementAndGet();
            if (count >= maxAttempts) {
                lockedUntil = Instant.now().plus(lockDuration);
                // On remet le compteur a zero en meme temps qu'on bloque : sinon, a la fin du
                // blocage le compteur serait toujours au max, et le premier essai suivant
                // rebloquerait tout de suite.
                failures.set(0);
            }
            return count;
        }

        long remainingLockSeconds() {
            long seconds = Duration.between(Instant.now(), lockedUntil).toSeconds();
            return Math.max(0, seconds);
        }

        boolean isExpired() {
            return failures.get() == 0 && remainingLockSeconds() == 0;
        }
    }
}
