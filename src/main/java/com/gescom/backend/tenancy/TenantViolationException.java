package com.gescom.backend.tenancy;

import com.gescom.backend.exception.LocalizedException;

/**
 * Levee lorsqu'une ecriture viserait une entite appartenant a une autre entreprise.
 *
 * Distincte de {@code ResourceNotFoundException} : en lecture on fait disparaitre la
 * donnee, mais une ecriture hors perimetre traduit une anomalie franche — code fautif ou
 * tentative deliberee — qui doit etre rejetee et tracee, pas masquee en 404.
 * Mappee en 403 par {@code GlobalExceptionHandler}.
 */
public class TenantViolationException extends RuntimeException implements LocalizedException {

    public TenantViolationException() {
        super("Operation refusee : la ressource visee appartient a une autre entreprise");
    }

    @Override
    public String getMessageKey() {
        return "tenant.violation";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
