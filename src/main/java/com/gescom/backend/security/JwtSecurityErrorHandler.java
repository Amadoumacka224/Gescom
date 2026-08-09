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
 * Réponses d'erreur de la chaîne de filtres de sécurité : 401 faute d'authentification,
 * 403 faute de droits.
 *
 * Ces deux refus sont prononcés en amont des contrôleurs : ils ne passent donc PAS par
 * {@link com.gescom.backend.exception.GlobalExceptionHandler}. Sans eux, Spring renvoie une
 * redirection HTML (inadaptée à une API REST) ou une 403 vide. Un seul composant les rend
 * tous les deux, au même format {@link ErrorResponse} que le reste de l'API — c'est la même
 * décision de présentation, elle n'a pas à être écrite à deux endroits.
 *
 * L'{@link ObjectMapper} géré par Spring est réutilisé (il sait sérialiser les types
 * date/heure et évite une instanciation à chaque requête).
 */
@Component
public class JwtSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Requête non authentifiée sur une ressource protégée. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentification requise pour accéder à cette ressource.");
    }

    /** Utilisateur authentifié mais dépourvu du rôle requis. */
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
