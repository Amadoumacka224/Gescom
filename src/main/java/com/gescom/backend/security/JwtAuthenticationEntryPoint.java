package com.gescom.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gescom.backend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Point d'entrée déclenché quand une requête non authentifiée tente d'accéder à une
 * ressource protégée. Au lieu de la redirection HTML par défaut de Spring (inadaptée à une
 * API REST), il renvoie une réponse JSON 401 au même format ({@link ErrorResponse}) que le
 * reste de l'API. L'{@link ObjectMapper} géré par Spring est réutilisé (il sait sérialiser
 * les types date/heure et évite une instanciation à chaque requête).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentification requise pour accéder à cette ressource.",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
