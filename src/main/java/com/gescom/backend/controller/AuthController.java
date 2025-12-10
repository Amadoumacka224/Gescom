package com.gescom.backend.controller;

import com.gescom.backend.dto.LoginRequest;
import com.gescom.backend.dto.LoginResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.security.JwtUtils;
import com.gescom.backend.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ActivityLogService activityLogService;

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User userDetails = (User) authentication.getPrincipal();

        // Log the login activity
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
            // Don't fail login if logging fails
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
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Log the logout activity before clearing context
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
                // Don't fail logout if logging fails
            }
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().body("{\"message\": \"Logout successful\"}");
    }
}
