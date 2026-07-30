package com.gescom.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gescom.backend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Déclenché quand un utilisateur authentifié tente d'accéder à une ressource pour laquelle
 * il n'a pas le rôle requis (ex : un non-admin sur /api/admin/**). Cette exception est levée
 * dans la chaîne de filtres de sécurité, en amont des contrôleurs : elle n'est donc PAS
 * interceptée par {@link com.gescom.backend.exception.GlobalExceptionHandler}.
 * Sans ce handler, Spring renvoie une réponse 403 vide ; on renvoie ici le même
 * {@link ErrorResponse} JSON que le reste de l'API.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.FORBIDDEN.value());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Vous n'avez pas les droits nécessaires pour accéder à cette ressource.",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
