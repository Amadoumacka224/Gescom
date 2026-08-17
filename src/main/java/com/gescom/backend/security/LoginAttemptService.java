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
 * Limitation des tentatives de connexion.
 *
 * {@code /api/auth/login} etait la seule route ouverte sans jeton et n'opposait aucune limite :
 * ni compteur d'echecs, ni verrouillage, ni ralentissement. Le cout BCrypt (12 tours, quelques
 * centaines de millisecondes) ralentit un attaquant sans jamais l'arreter — sur une nuit, cela
 * laisse largement de quoi essayer les mots de passe courants sur un compte connu.
 *
 * <h2>Ce qui est compte</h2>
 *
 * Le compteur porte sur le couple <b>identifiant + adresse d'origine</b>, et non sur l'un des
 * deux seuls. Ce choix est le coeur du dispositif :
 *
 * <ul>
 *   <li><b>Par identifiant seul</b>, n'importe qui pourrait verrouiller le compte {@code admin}
 *       depuis l'exterieur en repetant de faux mots de passe : la protection deviendrait une
 *       arme de deni de service contre l'utilisateur legitime.</li>
 *   <li><b>Par adresse seule</b>, tous les postes derriere une meme sortie internet — le cas
 *       ordinaire d'un commerce — partageraient le meme quota, et l'erreur de frappe d'un
 *       collegue fermerait la caisse du voisin.</li>
 *   <li><b>Par couple</b>, l'attaquant qui martele un compte depuis une machine est bloque
 *       pour ce compte-la, tandis que son titulaire garde l'acces depuis son propre poste.</li>
 * </ul>
 *
 * <h2>Ce que cela n'arrete pas</h2>
 *
 * Une attaque repartie sur de nombreuses adresses passe au travers, chaque couple restant sous
 * le seuil. La reponse a ce cas est un pare-feu applicatif ou une limitation en amont, pas un
 * compteur applicatif — c'est une limite assumee, pas un oubli.
 *
 * <h2>Etat en memoire</h2>
 *
 * Le compteur vit dans l'instance : il repart a zero au redemarrage, et deux instances
 * derriere un repartiteur comptent chacune de leur cote. Pour un outil interne c'est
 * suffisant, et cela evite d'ajouter une dependance (Redis) et une table pour une protection
 * dont l'effet recherche est de ralentir, pas de tenir un registre. Le passage a un compteur
 * partage se ferait en remplacant la seule carte ci-dessous.
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /**
     * Au-dela de cette taille, les entrees perimees sont balayees. Le seuil n'est pas une
     * limite fonctionnelle mais un garde-fou memoire : sans lui, une attaque changeant
     * d'identifiant a chaque essai ferait croitre la carte indefiniment.
     */
    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration lockDuration;

    /** Une entree par couple identifiant/adresse. */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.lock-duration-seconds:900}") long lockDurationSeconds) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofSeconds(lockDurationSeconds);
    }

    /**
     * Verifie qu'une tentative est permise, et leve {@link TooManyAttemptsException} sinon.
     *
     * A appeler AVANT toute verification du mot de passe : verifier d'abord reviendrait a
     * offrir a l'attaquant le calcul BCrypt qu'on cherche justement a lui refuser.
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
     * Enregistre un echec. Au {@code maxAttempts}-ieme, le couple est verrouille pour
     * {@code lockDuration}.
     *
     * Le verrou expire de lui-meme : aucun deverrouillage manuel n'est necessaire, et un
     * utilisateur qui s'est simplement trompe retrouve l'acces sans appeler personne.
     */
    public void recordFailure(String username, String clientIp) {
        purgeIfCrowded();
        String key = key(username, clientIp);
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        int count = attempt.registerFailure(lockDuration, maxAttempts);
        if (count >= maxAttempts) {
            // Trace au niveau WARN : c'est le signal qu'un compte est vise. Le journal
            // d'activite, lui, ne consigne que les connexions REUSSIES.
            log.warn("Connexion verrouillee pour « {} » depuis {} apres {} echecs",
                    username, clientIp, count);
        }
    }

    /** Efface le compteur apres une connexion reussie : seuls les echecs consecutifs comptent. */
    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    /**
     * Identifiant normalise en minuscules : sans cela, alterner « Admin » et « admin »
     * ouvrirait autant de compteurs distincts que de variantes de casse.
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

    /** Compteur d'un couple : nombre d'echecs consecutifs et fin de verrou eventuelle. */
    private static final class Attempt {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile Instant lockedUntil = Instant.EPOCH;

        int registerFailure(Duration lockDuration, int maxAttempts) {
            int count = failures.incrementAndGet();
            if (count >= maxAttempts) {
                lockedUntil = Instant.now().plus(lockDuration);
                // Remise a zero du compteur en meme temps que la pose du verrou : sans cela,
                // le verrou expire mais le compteur reste au maximum, et le tout premier essai
                // suivant — meme correct dans l'intention — reverrouillerait aussitot.
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
