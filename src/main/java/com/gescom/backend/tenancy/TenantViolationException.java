package com.gescom.backend.tenancy;

import com.gescom.backend.exception.LocalizedException;

/**
 * Lancee quand on essaie de modifier une entite qui appartient a une autre entreprise.
 *
 * Differente de {@code ResourceNotFoundException} : en lecture on fait comme si la donnee
 * n'existait pas, mais une ecriture sur une autre entreprise veut dire qu'il y a un vrai
 * probleme (bug ou tentative volontaire), qu'on doit refuser et logger, pas cacher en 404.
 * Transformee en 403 par {@code GlobalExceptionHandler}.
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
