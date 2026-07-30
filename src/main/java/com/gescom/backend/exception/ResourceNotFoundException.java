package com.gescom.backend.exception;

/**
 * Levée quand une entité demandée est introuvable (par id ou par un autre champ).
 * Mappée en HTTP 404 par {@link GlobalExceptionHandler}.
 *
 * Comme {@link DuplicateResourceException}, `resource` et `field` sont des clés de traduction :
 * le nom de l'entité fait partie du message vu par l'utilisateur.
 */
public class ResourceNotFoundException extends RuntimeException implements LocalizedException {

    private final String messageKey;
    private final Object[] messageArgs;

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " non trouvé(e) avec l'id: " + id);
        this.messageKey = "resource.notFound.byId";
        this.messageArgs = new Object[] { "resource." + resource, id };
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " non trouvé(e) avec " + field + ": " + value);
        this.messageKey = "resource.notFound.byField";
        this.messageArgs = new Object[] { "resource." + resource, "field." + field, value };
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
