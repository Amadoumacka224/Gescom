package com.gescom.backend.security;

import com.gescom.backend.exception.LocalizedException;

/**
 * Levée lorsqu'un caissier tente d'agir sur une vente enregistrée par un autre caissier.
 *
 * Pendant de {@code TenantViolationException} à l'échelle de l'opérateur : en lecture, la
 * vente d'un collègue est traitée comme inexistante (404) ; en écriture — modification,
 * confirmation, annulation, encaissement — le refus est explicite et tracé, car il ne peut
 * venir que d'un appel forgé à la main ou d'un défaut de code.
 * Mappée en 403 par {@code GlobalExceptionHandler}.
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
