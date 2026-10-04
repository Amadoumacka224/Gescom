package com.gescom.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gescom.backend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Réponses d'erreur des filtres de sécurité : 401 si pas authentifié, 403 si pas les droits.
 *
 * Ces deux refus arrivent avant les contrôleurs, donc ils ne passent PAS par
 * {@link com.gescom.backend.exception.GlobalExceptionHandler}. Sans cette classe, Spring
 * renvoie une redirection HTML (pas adaptée à une API REST) ou une 403 vide. Un seul
 * composant gère les deux, avec le même format {@link ErrorResponse} que le reste de l'API :
 * c'est la même logique, pas besoin de l'écrire deux fois.
 *
 * On réutilise l'{@link ObjectMapper} de Spring (il sait gérer les dates et évite d'en créer
 * un à chaque requête).
 */
@Component
public class JwtSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Requête sans authentification sur une ressource protégée. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentification requise pour accéder à cette ressource.");
    }

    /** Utilisateur connecté mais qui n'a pas le bon rôle. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN,
                "Vous n'avez pas les droits nécessaires pour accéder à cette ressource.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(status.value());

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
