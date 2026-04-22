package com.gescom.backend.controller;

import com.gescom.backend.dto.LoginRequest;
import com.gescom.backend.dto.LoginResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.security.JwtUtils;
import com.gescom.backend.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final ActivityLogService activityLogService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtils jwtUtils,
                          ActivityLogService activityLogService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.activityLogService = activityLogService;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

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
        } catch (DisabledException e) {
            log.warn("Tentative de connexion sur un compte désactivé: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Ce compte est désactivé. Contactez un administrateur."));
        } catch (BadCredentialsException e) {
            log.warn("Échec de connexion (identifiants invalides): {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Nom d'utilisateur ou mot de passe incorrect."));
        } catch (AuthenticationException e) {
            log.warn("Échec de connexion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Échec de l'authentification."));
        }
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
