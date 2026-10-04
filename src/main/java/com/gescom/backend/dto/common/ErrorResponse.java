package com.gescom.backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Format JSON commun à toutes les réponses d'erreur de l'API.
 * Comme ça, le front reçoit toujours la même structure, peu importe d'où vient l'erreur
 * (handler global, filtre de sécurité, etc.).
 *
 * Les champs optionnels ({@code fieldErrors}, {@code traceId}) ne sont pas mis dans le JSON
 * quand ils sont null, grâce à {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Date et heure de l'erreur côté serveur. */
    private final LocalDateTime timestamp;
    /** Code HTTP (ex : 404). */
    private final int status;
    /** Libellé court du statut HTTP (ex : "Not Found"). */
    private final String error;
    /** Message lisible pour l'utilisateur / le front. */
    private final String message;
    /** Chemin de la requête qui a provoqué l'erreur. */
    private final String path;
    /** Erreurs par champ pour les erreurs de validation (sinon null). */
    private final Map<String, String> fieldErrors;
    /** Id pour retrouver l'erreur dans les logs, seulement pour les erreurs internes (500). */
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
