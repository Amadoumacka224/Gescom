package com.gescom.backend.security;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Payment;
import com.gescom.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cloisonnement d'un caissier à ses propres ventes.
 *
 * Deuxième niveau d'isolement, sous celui des entreprises : {@code TenantContext} détermine
 * QUELLE entreprise la requête peut voir, celui-ci détermine QUELLE PART de cette entreprise
 * un caissier peut voir. L'ADMIN, lui, supervise l'ensemble — {@link #restrictedUserId()}
 * renvoie alors {@code null}, qui signifie « aucune restriction », exactement comme un
 * contexte d'entreprise vide.
 *
 * La propriété d'une opération remonte toujours à {@code Order.createdBy} : une facture, une
 * livraison ou un paiement carte n'existent que par la vente dont ils découlent, et suivent
 * donc le caissier qui l'a saisie plutôt que celui qui a produit le document. Sans quoi un
 * caissier perdrait la facture de sa propre vente dès qu'un collègue l'aurait éditée.
 *
 * L'identité est lue sur le principal authentifié — l'entité {@code User} chargée en base par
 * {@code JwtAuthenticationFilter} —, jamais sur un identifiant fourni par l'appelant : c'est
 * ce qui rend le cloisonnement insensible à un appel direct de l'API.
 */
@Component("cashierScope")
public class CashierScope {

    private static final Logger log = LoggerFactory.getLogger(CashierScope.class);

    /** Utilisateur authentifié, ou {@code null} hors requête authentifiée. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal() instanceof User user ? user : null;
    }

    /**
     * Identifiant du caissier auquel la requête est restreinte, ou {@code null} si elle ne
     * l'est pas (ADMIN, et traitements hors requête qui ne servent aucun écran).
     *
     * C'est la valeur à passer aux requêtes de liste : {@code null} y désactive le filtre.
     */
    public Long restrictedUserId() {
        User user = currentUser();
        return isCashier(user) ? user.getId() : null;
    }

    /** Vrai si la requête en cours est celle d'un caissier, donc limitée à ses propres ventes. */
    public boolean isRestricted() {
        return isCashier(currentUser());
    }

    private boolean isCashier(User user) {
        return user != null && user.getRole() == User.Role.CAISSIER;
    }

    // --- Appartenance d'une opération -----------------------------------------

    /** Vrai si la requête en cours a le droit de voir cette vente. */
    public boolean canAccess(Order order) {
        Long restricted = restrictedUserId();
        if (restricted == null) {
            return true;
        }
        // Une vente sans créateur (import, reprise de données) n'appartient à personne :
        // elle relève de la supervision, pas de la caisse.
        return order != null
                && order.getCreatedBy() != null
                && restricted.equals(order.getCreatedBy().getId());
    }

    public boolean canAccess(Invoice invoice) {
        return invoice != null && canAccess(invoice.getOrder());
    }

    public boolean canAccess(Delivery delivery) {
        return delivery != null && canAccess(delivery.getOrder());
    }

    public boolean canAccess(Payment payment) {
        return payment != null && canAccess(payment.getInvoice());
    }

    // --- Lecture : la vente d'autrui se comporte comme une vente inexistante ---

    /**
     * Ne laisse passer la ressource que si la requête y a droit, sinon renvoie un
     * {@link Optional} vide — le contrôleur en tire un 404, indiscernable d'un identifiant
     * qui n'existe pas. Une lecture hors périmètre ne doit rien révéler, pas même l'existence
     * de la vente d'un collègue.
     */
    public <T> Optional<T> filterReadable(Optional<T> found, Function<T, Order> saleOf) {
        // Sans restriction, la vente n'est même pas lue : la résoudre initialiserait les proxies
        // de la chaîne (paiement → facture → commande) pour une valeur aussitôt écartée.
        if (!isRestricted()) {
            return found;
        }
        return found.filter(item -> canAccess(saleOf.apply(item)));
    }

    /** Restreint une liste au périmètre de la requête (sans copie inutile pour un ADMIN). */
    public <T> List<T> filterReadable(Collection<T> items, Function<T, Order> saleOf) {
        if (!isRestricted()) {
            return items instanceof List<T> list ? list : List.copyOf(items);
        }
        return items.stream().filter(item -> canAccess(saleOf.apply(item))).toList();
    }

    // --- Écriture : refus explicite et tracé ----------------------------------

    /**
     * Exige que la requête ait le droit d'agir sur cette vente. Contrairement à la lecture,
     * l'écriture hors périmètre est rejetée franchement : l'interface n'y mène jamais, une
     * telle tentative traduit un appel forgé ou une régression.
     */
    public void requireAccess(Order order) {
        if (!canAccess(order)) {
            log.warn("Caissier {} : tentative d'écriture sur la commande {} d'un autre opérateur",
                    restrictedUserId(), order != null ? order.getOrderNumber() : null);
            throw new OwnershipViolationException();
        }
    }

    public void requireAccess(Invoice invoice) {
        requireAccess(invoice != null ? invoice.getOrder() : null);
    }

    public void requireAccess(Delivery delivery) {
        requireAccess(delivery != null ? delivery.getOrder() : null);
    }

    public void requireAccess(Payment payment) {
        requireAccess(payment != null ? payment.getInvoice() : null);
    }
}
