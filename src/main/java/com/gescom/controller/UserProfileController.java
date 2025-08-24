package com.gescom.controller;

import com.gescom.entity.User;
import com.gescom.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/user")
public class UserProfileController {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);
    private static final String UPLOAD_DIR = "uploads/avatars/";

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Affiche la page de profil de l'utilisateur connecté
     */
    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        try {
            User currentUser = getCurrentUser(authentication);
            
            // Calculer quelques statistiques fictives pour l'exemple
            Map<String, Object> stats = calculateUserStats(currentUser);
            
            model.addAttribute("user", currentUser);
            model.addAttribute("stats", stats);
            model.addAttribute("pageTitle", "Mon Profil");
            
            logger.info("Affichage du profil pour l'utilisateur: {}", currentUser.getUsername());
            return "user/profile";
            
        } catch (Exception e) {
            logger.error("Erreur lors de l'affichage du profil: {}", e.getMessage(), e);
            return "redirect:/dashboard?error=profile_load_failed";
        }
    }

    /**
     * Met à jour les informations personnelles de l'utilisateur
     */
    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "emailNotifications", required = false) boolean emailNotifications,
            @RequestParam(value = "smsNotifications", required = false) boolean smsNotifications,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            User currentUser = getCurrentUser(authentication);
            
            // Mettre à jour les informations
            currentUser.setFirstName(firstName);
            currentUser.setLastName(lastName);
            currentUser.setEmail(email);
            currentUser.setPhoneNumber(phoneNumber);
            
            // Mettre à jour les préférences (si votre entité User les supporte)
            // currentUser.setLanguage(language);
            // currentUser.setTimezone(timezone);
            // currentUser.setEmailNotifications(emailNotifications);
            // currentUser.setSmsNotifications(smsNotifications);
            
            currentUser.setUpdatedAt(LocalDateTime.now());
            
            userService.updateUser(currentUser.getId(), currentUser);
            
            logger.info("Profil mis à jour pour l'utilisateur: {}", currentUser.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Profil mis à jour avec succès.");
            
        } catch (Exception e) {
            logger.error("Erreur lors de la mise à jour du profil: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur lors de la mise à jour du profil.");
        }

        return "redirect:/user/profile";
    }

    /**
     * Change le mot de passe de l'utilisateur
     */
    @PostMapping("/profile/change-password")
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            User currentUser = getCurrentUser(authentication);
            
            // Vérifier l'ancien mot de passe
            if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
                redirectAttributes.addFlashAttribute("passwordError", "Le mot de passe actuel est incorrect.");
                return "redirect:/user/profile";
            }
            
            // Vérifier que les nouveaux mots de passe correspondent
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("passwordError", "Les nouveaux mots de passe ne correspondent pas.");
                return "redirect:/user/profile";
            }
            
            // Vérifier la force du mot de passe
            if (!isPasswordStrong(newPassword)) {
                redirectAttributes.addFlashAttribute("passwordError", "Le mot de passe doit contenir au moins 8 caractères avec majuscules, minuscules, chiffres et caractères spéciaux.");
                return "redirect:/user/profile";
            }
            
            // Changer le mot de passe
            currentUser.setPassword(passwordEncoder.encode(newPassword));
            currentUser.setUpdatedAt(LocalDateTime.now());
            
            userService.updateUser(currentUser.getId(), currentUser);
            
            logger.info("Mot de passe changé pour l'utilisateur: {}", currentUser.getUsername());
            redirectAttributes.addFlashAttribute("passwordSuccess", "Mot de passe changé avec succès.");
            
        } catch (Exception e) {
            logger.error("Erreur lors du changement de mot de passe: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("passwordError", "Erreur lors du changement de mot de passe.");
        }

        return "redirect:/user/profile";
    }

    /**
     * Upload d'avatar
     */
    @PostMapping("/profile/upload-avatar")
    public String uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            User currentUser = getCurrentUser(authentication);
            
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("avatarError", "Veuillez sélectionner un fichier.");
                return "redirect:/user/profile";
            }
            
            // Vérifier le type de fichier
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("avatarError", "Le fichier doit être une image.");
                return "redirect:/user/profile";
            }
            
            // Vérifier la taille (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                redirectAttributes.addFlashAttribute("avatarError", "L'image ne peut pas dépasser 5MB.");
                return "redirect:/user/profile";
            }
            
            // Créer le répertoire s'il n'existe pas
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Générer un nom unique pour le fichier
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? 
                originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = currentUser.getId() + "_" + UUID.randomUUID().toString() + extension;
            
            // Sauvegarder le fichier
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Mettre à jour l'utilisateur
            String avatarUrl = "/uploads/avatars/" + filename;
            // currentUser.setAvatarUrl(avatarUrl); // Si votre entité User supporte les avatars
            currentUser.setUpdatedAt(LocalDateTime.now());
            
            userService.updateUser(currentUser.getId(), currentUser);
            
            logger.info("Avatar uploadé pour l'utilisateur: {} -> {}", currentUser.getUsername(), filename);
            redirectAttributes.addFlashAttribute("avatarSuccess", "Avatar mis à jour avec succès.");
            
        } catch (IOException e) {
            logger.error("Erreur lors de l'upload d'avatar: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("avatarError", "Erreur lors de l'upload de l'avatar.");
        } catch (Exception e) {
            logger.error("Erreur lors de l'upload d'avatar: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("avatarError", "Erreur lors de l'upload de l'avatar.");
        }

        return "redirect:/user/profile";
    }

    /**
     * API pour vérifier la force du mot de passe
     */
    @PostMapping("/profile/check-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkPasswordStrength(@RequestParam("password") String password) {
        Map<String, Object> response = new HashMap<>();
        
        int score = calculatePasswordScore(password);
        String strength = getPasswordStrengthLabel(score);
        boolean isStrong = score >= 4;
        
        response.put("score", score);
        response.put("strength", strength);
        response.put("isStrong", isStrong);
        response.put("requirements", getPasswordRequirements(password));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Récupère l'utilisateur actuel depuis l'authentification
     */
    private User getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + username));
    }

    /**
     * Calcule des statistiques fictives pour l'utilisateur
     */
    private Map<String, Object> calculateUserStats(User user) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", 42); // À remplacer par de vraies données
        stats.put("totalSpent", 1250.00);
        stats.put("favoritesCount", 8);
        stats.put("memberSince", user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate() : LocalDateTime.now().toLocalDate());
        return stats;
    }

    /**
     * Vérifie si un mot de passe est suffisamment fort
     */
    private boolean isPasswordStrong(String password) {
        return password.length() >= 8 &&
               password.matches(".*[a-z].*") &&
               password.matches(".*[A-Z].*") &&
               password.matches(".*\\d.*") &&
               password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    }

    /**
     * Calcule le score de force du mot de passe
     */
    private int calculatePasswordScore(String password) {
        int score = 0;
        
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        
        return Math.min(score, 5);
    }

    /**
     * Retourne le label de force du mot de passe
     */
    private String getPasswordStrengthLabel(int score) {
        switch (score) {
            case 0:
            case 1: return "Très faible";
            case 2: return "Faible";
            case 3: return "Moyen";
            case 4: return "Fort";
            case 5: return "Très fort";
            default: return "Inconnu";
        }
    }

    /**
     * Retourne les exigences du mot de passe
     */
    private Map<String, Boolean> getPasswordRequirements(String password) {
        Map<String, Boolean> requirements = new HashMap<>();
        requirements.put("minLength", password.length() >= 8);
        requirements.put("hasLowercase", password.matches(".*[a-z].*"));
        requirements.put("hasUppercase", password.matches(".*[A-Z].*"));
        requirements.put("hasDigit", password.matches(".*\\d.*"));
        requirements.put("hasSpecial", password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*"));
        return requirements;
    }
}