package com.gescom.backend.controller;

import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/init")
public class InitController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/create-test-users")
    public ResponseEntity<Map<String, Object>> createTestUsers() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Créer ou mettre à jour l'utilisateur admin
            User admin = userRepository.findByUsername("admin").orElse(new User());
            admin.setUsername("admin");
            admin.setEmail("admin@gescom.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFirstName("Admin");
            admin.setLastName("GESCOM");
            admin.setPhone("0600000000");
            admin.setRole(User.Role.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
            response.put("admin", "Utilisateur admin créé/mis à jour avec succès");

            // Créer ou mettre à jour l'utilisateur caissier
            User caissier = userRepository.findByUsername("caissier").orElse(new User());
            caissier.setUsername("caissier");
            caissier.setEmail("caissier@gescom.com");
            caissier.setPassword(passwordEncoder.encode("caissier123"));
            caissier.setFirstName("Caissier");
            caissier.setLastName("GESCOM");
            caissier.setPhone("0611111111");
            caissier.setRole(User.Role.CAISSIER);
            caissier.setActive(true);
            userRepository.save(caissier);
            response.put("caissier", "Utilisateur caissier créé/mis à jour avec succès");

            response.put("status", "success");
            response.put("message", "Utilisateurs de test créés/mis à jour avec les bons mots de passe");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Erreur: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Backend is running");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
