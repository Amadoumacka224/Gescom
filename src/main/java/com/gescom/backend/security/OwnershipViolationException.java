package com.gescom.backend.security;

import com.gescom.backend.exception.LocalizedException;

/**
 * Lancée quand un caissier essaie de modifier une vente faite par un autre caissier.
 *
 * C'est l'équivalent de {@code TenantViolationException} mais entre caissiers : en lecture,
 * la vente d'un collègue est traitée comme si elle n'existait pas (404) ; en écriture
 * (modification, confirmation, annulation, paiement), on refuse clairement et on garde une
 * trace, car ça ne peut venir que d'une requête fabriquée à la main ou d'un bug.
 * Transformée en 403 par {@code GlobalExceptionHandler}.
 */
public class OwnershipViolationException extends RuntimeException implements LocalizedException {

    public OwnershipViolationException() {
        super("Opération refusée : cette vente a été enregistrée par un autre caissier");
    }

    @Override
    public String getMessageKey() {
        return "ownership.violation";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
