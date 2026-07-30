package com.gescom.backend.exception;

/**
 * Levée à la création/modification quand une valeur censée être unique existe déjà
 * (ex : code produit, email, nom de catégorie). Mappée en HTTP 409 (Conflict).
 *
 * `resource` et `field` sont des clés de traduction (`resource.*`, `field.*`) et non des mots :
 * un message « Catégorie avec nom 'X' existe déjà » ne se traduit pas en remplaçant seulement
 * sa phrase porteuse, il faut aussi décliner le nom de l'entité et celui du champ.
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
