package com.gescom.backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Corps JSON unique pour toutes les réponses d'erreur de l'API.
 * Centraliser ce format garantit que le frontend reçoit toujours la même structure,
 * quel que soit l'endroit où l'erreur est produite (advice global, filtre de sécurité, etc.).
 *
 * Les champs optionnels ({@code fieldErrors}, {@code traceId}) sont omis du JSON quand
 * ils sont nuls grâce à {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Horodatage de l'erreur côté serveur. */
    private final LocalDateTime timestamp;
    /** Code HTTP (ex : 404). */
    private final int status;
    /** Libellé court du statut HTTP (ex : "Not Found"). */
    private final String error;
    /** Message lisible destiné à l'utilisateur / au frontend. */
    private final String message;
    /** Chemin de la requête à l'origine de l'erreur. */
    private final String path;
    /** Détail champ par champ pour les erreurs de validation (sinon null). */
    private final Map<String, String> fieldErrors;
    /** Identifiant de corrélation, présent uniquement pour les erreurs internes (500). */
    private final String traceId;

    private ErrorResponse(int status, String error, String message, String path,
                          Map<String, String> fieldErrors, String traceId) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
        this.traceId = traceId;
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, null, null);
    }

    public static ErrorResponse validation(int status, String error, String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, path, fieldErrors, null);
    }

    public static ErrorResponse internal(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(status, error, message, path, null, traceId);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public String getTraceId() {
        return traceId;
    }
}
