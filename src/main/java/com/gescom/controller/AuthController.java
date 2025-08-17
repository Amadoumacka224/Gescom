package com.gescom.controller;

import org.springframework.stereotype.Controller;

import com.gescom.dto.UserRegistrationDto;
import com.gescom.entity.User;
import com.gescom.service.UserService;
import com.gescom.exception.UserAlreadyExistsException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    /**
     * Route racine - Redirige selon l'état d'authentification
     */
    @GetMapping("/")
    public String home(Authentication authentication) {
        // Si l'utilisateur est connecté, rediriger vers le dashboard
        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser") &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().startsWith("ROLE_"))) {
            logger.info("Utilisateur connecté détecté, redirection vers dashboard: {}", authentication.getName());
            return "redirect:/dashboard";
        }
        
        // Sinon, rediriger vers la page de connexion
        logger.info("Utilisateur non connecté, redirection vers login");
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "expired", required = false) String expired,
            Model model,
            Authentication authentication) {

        // CORRECTION : Redirection uniquement si vraiment connecté
        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser") &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().startsWith("ROLE_"))) {
            return "redirect:/dashboard";
        }

        model.addAttribute("appName", "Application Commerciale");
        model.addAttribute("companyName", "Votre Entreprise");

        if (error != null) {
            model.addAttribute("errorMessage", "Nom d'utilisateur ou mot de passe invalide.");
        }

        if (logout != null) {
            model.addAttribute("successMessage", "Vous avez été déconnecté avec succès.");
        }

        if (expired != null) {
            model.addAttribute("warningMessage", "Votre session a expiré. Veuillez vous reconnecter.");
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String registrationPage(Model model) {
        model.addAttribute("appName", "Application Commerciale");
        model.addAttribute("userDto", new UserRegistrationDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(
            @Valid @ModelAttribute("userDto") UserRegistrationDto userDto,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        logger.info("Tentative d'inscription pour l'utilisateur: {}", userDto.getUsername());

        if (result.hasErrors()) {
            model.addAttribute("appName", "Application Commerciale");
            return "auth/register";
        }

        // Vérifier que les mots de passe correspondent
        if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "error.confirmPassword",
                    "Les mots de passe ne correspondent pas");
            model.addAttribute("appName", "Application Commerciale");
            return "auth/register";
        }

        try {
            // Créer l'utilisateur
            User user = new User(
                    userDto.getUsername(),
                    userDto.getPassword(),
                    userDto.getEmail(),
                    userDto.getFirstName(),
                    userDto.getLastName()
            );

            userService.createUser(user);

            logger.info("Utilisateur créé avec succès: {}", user.getUsername());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Inscription réussie ! Vous pouvez maintenant vous connecter.");

            return "redirect:/login";

        } catch (UserAlreadyExistsException e) {
            logger.warn("Tentative de création d'un utilisateur existant: {}", userDto.getUsername());

            if (e.getMessage().contains("nom d'utilisateur")) {
                result.rejectValue("username", "error.username", e.getMessage());
            } else if (e.getMessage().contains("email")) {
                result.rejectValue("email", "error.email", e.getMessage());
            }

            model.addAttribute("appName", "Application Commerciale");
            return "auth/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("appName", "Application Commerciale");
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam("email") String email,
            RedirectAttributes redirectAttributes) {

        logger.info("Demande de réinitialisation de mot de passe pour: {}", email);

        // TODO: Implémenter la logique de réinitialisation par email

        redirectAttributes.addFlashAttribute("successMessage",
                "Si cet email existe dans notre système, vous recevrez un lien de réinitialisation.");

        return "redirect:/forgot-password";
    }
}
