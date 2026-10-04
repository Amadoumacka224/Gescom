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
 * Limite un caissier à ses propres ventes.
 *
 * C'est un deuxième niveau de séparation, en dessous de celui des entreprises :
 * {@code TenantContext} dit QUELLE entreprise la requête peut voir, cette classe dit QUELLE
 * PARTIE de l'entreprise un caissier peut voir. L'ADMIN voit tout : {@link #restrictedUserId()}
 * renvoie alors {@code null}, qui veut dire « pas de restriction », comme un contexte
 * d'entreprise vide.
 *
 * Une opération appartient toujours au caissier de {@code Order.createdBy} : une facture,
 * une livraison ou un paiement carte n'existent que grâce à la vente, donc ils suivent le
 * caissier qui a fait la vente et pas celui qui a créé le document. Sinon un caissier ne
 * verrait plus la facture de sa propre vente dès qu'un collègue l'aurait créée.
 *
 * L'utilisateur est lu dans le principal authentifié (l'entité {@code User} chargée en base
 * par {@code JwtAuthenticationFilter}), jamais dans un id envoyé par l'appelant : comme ça,
 * appeler l'API directement ne permet pas de contourner la règle.
 */
@Component("cashierScope")
public class CashierScope {

    private static final Logger log = LoggerFactory.getLogger(CashierScope.class);

    /** Utilisateur connecté, ou {@code null} en dehors d'une requête authentifiée. */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal() instanceof User user ? user : null;
    }

    /**
     * Id du caissier auquel la requête est limitée, ou {@code null} si elle ne l'est pas
     * (ADMIN, ou traitements hors requête qui ne servent aucun écran).
     *
     * C'est la valeur à passer aux requêtes de liste : {@code null} désactive le filtre.
     */
    public Long restrictedUserId() {
        User user = currentUser();
        return isCashier(user) ? user.getId() : null;
    }

    /** Vrai si c'est un caissier qui fait la requête, donc limité à ses propres ventes. */
    public boolean isRestricted() {
        return isCashier(currentUser());
    }

    private boolean isCashier(User user) {
        return user != null && user.getRole() == User.Role.CAISSIER;
    }

    // --- À qui appartient une opération ---------------------------------------

    /** Vrai si la requête a le droit de voir cette vente. */
    public boolean canAccess(Order order) {
        Long restricted = restrictedUserId();
        if (restricted == null) {
            return true;
        }
        // Une vente sans créateur (import, reprise de données) n'appartient à personne :
        // c'est pour l'admin, pas pour la caisse.
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

    // --- Lecture : la vente d'un autre est traitée comme si elle n'existait pas ---

    /**
     * Ne garde la ressource que si la requête y a droit, sinon renvoie un {@link Optional}
     * vide. Le contrôleur renvoie alors un 404, comme pour un id qui n'existe pas : une
     * lecture interdite ne doit rien révéler, même pas que la vente d'un collègue existe.
     */
    public <T> Optional<T> filterReadable(Optional<T> found, Function<T, Order> saleOf) {
        // Sans restriction, on ne lit même pas la vente : ça chargerait les proxies
        // (paiement → facture → commande) pour rien.
        if (!isRestricted()) {
            return found;
        }
        return found.filter(item -> canAccess(saleOf.apply(item)));
    }

    /** Filtre une liste selon ce que la requête peut voir (pas de copie inutile pour un ADMIN). */
    public <T> List<T> filterReadable(Collection<T> items, Function<T, Order> saleOf) {
        if (!isRestricted()) {
            return items instanceof List<T> list ? list : List.copyOf(items);
        }
        return items.stream().filter(item -> canAccess(saleOf.apply(item))).toList();
    }

    // --- Écriture : refus clair et loggé --------------------------------------

    /**
     * Vérifie que la requête a le droit de modifier cette vente. Contrairement à la lecture,
     * on refuse clairement : l'interface ne permet jamais d'arriver là, donc c'est forcément
     * une requête fabriquée à la main ou un bug.
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
