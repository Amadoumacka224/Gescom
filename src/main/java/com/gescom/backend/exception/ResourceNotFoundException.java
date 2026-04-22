package com.gescom.backend.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " non trouvé(e) avec l'id: " + id);
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " non trouvé(e) avec " + field + ": " + value);
    }
}
