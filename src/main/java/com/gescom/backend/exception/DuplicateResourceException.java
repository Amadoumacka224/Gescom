package com.gescom.backend.exception;

/**
 * Lancée à la création ou la modification quand une valeur qui doit être unique existe déjà
 * (ex : code produit, email, nom de catégorie). Transformée en HTTP 409 (Conflict).
 *
 * `resource` et `field` sont des clés de traduction (`resource.*`, `field.*`) et pas des mots :
 * pour traduire « Catégorie avec nom 'X' existe déjà », il ne suffit pas de traduire la
 * phrase, il faut aussi traduire le nom de l'entité et celui du champ.
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resource, String field, String value) {
        super(
                "resource.duplicate",
                new Object[] { "resource." + resource, "field." + field, value },
                resource + " avec " + field + " '" + value + "' existe déjà"
        );
    }
}
