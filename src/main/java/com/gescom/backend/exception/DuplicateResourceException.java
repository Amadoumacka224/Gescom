package com.gescom.backend.exception;

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String resource, String field, String value) {
        super(resource + " avec " + field + " '" + value + "' existe déjà");
    }
}
