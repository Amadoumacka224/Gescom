package com.gescom.backend.controller;

import com.gescom.backend.dto.auth.LoginRequest;
import com.gescom.backend.dto.auth.LoginResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.security.JwtUtils;
import com.gescom.backend.security.LoginAttemptService;
import com.gescom.backend.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentification", description = "Connexion et deconnexion, seules routes ouvertes sans jeton")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final ActivityLogService activityLogService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils,
                          ActivityLogService activityLogService,
                          LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.activityLogService = activityLogService;
        this.loginAttemptService = loginAttemptService;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    // Les erreurs d'authentification (BadCredentials, DisabledException…) ne sont pas
    // attrapées ici : elles remontent au GlobalExceptionHandler qui renvoie un ErrorResponse
    // (401) comme pour le reste de l'API, donc le front n'a qu'un seul format d'erreur à gérer.
    //
    // Le try/catch sert seulement à compter les tentatives : on enregistre l'échec puis on
    // relance l'exception, sans changer ce que reçoit l'appelant.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                                          HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // Avant de vérifier le mot de passe : si on vérifiait après, l'attaquant aurait quand
        // même droit au calcul BCrypt qu'on veut justement lui refuser.
        loginAttemptService.checkAllowed(loginRequest.getUsername(), clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        } catch (AuthenticationException e) {
            // On compte tous les refus, même un compte désactivé : savoir qu'un identifiant
            // existe mais est suspendu, c'est déjà une info pour un attaquant, pas de raison
            // de lui laisser des essais illimités pour le vérifier.
            loginAttemptService.recordFailure(loginRequest.getUsername(), clientIp);
            throw e;
        }

        loginAttemptService.recordSuccess(loginRequest.getUsername(), clientIp);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User userDetails = (User) authentication.getPrincipal();

        try {
            activityLogService.logActivity(
                userDetails.getId(),
                ActivityLog.ActionType.LOGIN,
                "User",
                userDetails.getId(),
                "Connexion de l'utilisateur " + userDetails.getUsername(),
                null,
                getClientIp(request)
            );
        } catch (Exception e) {
            log.warn("Échec du log de connexion: {}", e.getMessage());
        }

        return ResponseEntity.ok(new LoginResponse(
                jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                userDetails.getEmail(),
                userDetails.getPhone(),
                userDetails.getRole().name()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logoutUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            try {
                activityLogService.logActivity(
                    user.getId(),
                    ActivityLog.ActionType.LOGOUT,
                    "User",
                    user.getId(),
                    "Déconnexion de l'utilisateur " + user.getUsername(),
                    null,
                    getClientIp(request)
                );
            } catch (Exception e) {
                log.warn("Échec du log de déconnexion: {}", e.getMessage());
            }
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}
