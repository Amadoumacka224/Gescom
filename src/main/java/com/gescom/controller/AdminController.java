package com.gescom.controller;

import com.gescom.entity.User;
import com.gescom.entity.Role;
import com.gescom.repository.UserRepository;
import com.gescom.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            Model model) {

        try {
            // Récupérer tous les utilisateurs
            List<User> allUsers = userRepository.findAll();

            // Filtrage par recherche
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                allUsers = allUsers.stream()
                        .filter(user ->
                                user.getUsername().toLowerCase().contains(searchLower) ||
                                        user.getEmail().toLowerCase().contains(searchLower) ||
                                        user.getFullName().toLowerCase().contains(searchLower))
                        .collect(Collectors.toList());
            }

            // Filtrage par rôle
            if (role != null && !role.trim().isEmpty()) {
                allUsers = allUsers.stream()
                        .filter(user -> user.hasRole(role))
                        .collect(Collectors.toList());
            }

            // Filtrage par statut actif
            if (active != null) {
                allUsers = allUsers.stream()
                        .filter(user -> user.isEnabled() == active)
                        .collect(Collectors.toList());
            }

            // Tri
            switch (sortBy) {
                case "username" -> allUsers.sort((a, b) -> sortDir.equals("desc") ?
                        b.getUsername().compareTo(a.getUsername()) :
                        a.getUsername().compareTo(b.getUsername()));
                case "email" -> allUsers.sort((a, b) -> sortDir.equals("desc") ?
                        b.getEmail().compareTo(a.getEmail()) :
                        a.getEmail().compareTo(b.getEmail()));
                case "createdAt" -> allUsers.sort((a, b) -> {
                    LocalDateTime dateA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                    LocalDateTime dateB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                    return sortDir.equals("desc") ? dateB.compareTo(dateA) : dateA.compareTo(dateB);
                });
                default -> allUsers.sort((a, b) -> sortDir.equals("desc") ?
                        b.getLastName().compareTo(a.getLastName()) :
                        a.getLastName().compareTo(b.getLastName()));
            }

            // Pagination manuelle
            int start = Math.min(page * size, allUsers.size());
            int end = Math.min(start + size, allUsers.size());
            List<User> usersPage = allUsers.subList(start, end);

            // Calculs pour la pagination
            int totalPages = (int) Math.ceil((double) allUsers.size() / size);
            boolean hasNext = page < totalPages - 1;
            boolean hasPrevious = page > 0;

            // Statistiques
            long totalUsers = allUsers.size();
            long activeUsers = allUsers.stream().filter(User::isEnabled).count();
            long adminUsers = allUsers.stream().filter(u -> u.hasRole("ADMIN")).count();
            long managerUsers = allUsers.stream().filter(u -> u.hasRole("MANAGER")).count();
            long commercialUsers = allUsers.stream().filter(u -> u.hasRole("USER")).count();

            // Récupérer tous les rôles pour les filtres
            List<Role> allRoles = roleRepository.findAll();

            // Ajouter les attributs au modèle
            model.addAttribute("users", usersPage);
            model.addAttribute("allRoles", allRoles);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrevious", hasPrevious);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("search", search);
            model.addAttribute("selectedRole", role);
            model.addAttribute("activeFilter", active);
            model.addAttribute("size", size);

            // Statistiques
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("activeUsers", activeUsers);
            model.addAttribute("adminUsers", adminUsers);
            model.addAttribute("managerUsers", managerUsers);
            model.addAttribute("commercialUsers", commercialUsers);

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des utilisateurs: " + e.getMessage());
        }

        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newUser(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("isEdit", false);

        // Liste des rôles disponibles
        List<Role> allRoles = roleRepository.findAll();
        model.addAttribute("allRoles", allRoles);

        return "admin/users/form";
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
            return "redirect:/admin/users";
        }

        model.addAttribute("user", userOpt.get());
        return "admin/users/detail";
    }

    @GetMapping("/{id}/edit")
    public String editUser(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
            return "redirect:/admin/users";
        }

        model.addAttribute("user", userOpt.get());
        model.addAttribute("isEdit", true);

        // Liste des rôles disponibles
        List<Role> allRoles = roleRepository.findAll();
        model.addAttribute("allRoles", allRoles);

        return "admin/users/form";
    }

    @PostMapping
    public String saveUser(
            @Valid @ModelAttribute User user,
            BindingResult result,
            @RequestParam(required = false) List<Long> roleIds,
            @RequestParam(required = false) String newPassword,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Validation personnalisée
        if (user.getId() == null) { // Nouvel utilisateur
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                result.rejectValue("username", "error.user", "Ce nom d'utilisateur existe déjà");
            }
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                result.rejectValue("email", "error.user", "Cet email existe déjà");
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                result.rejectValue("password", "error.user", "Le mot de passe est obligatoire pour un nouvel utilisateur");
            }
        } else { // Modification
            Optional<User> existingByUsername = userRepository.findByUsername(user.getUsername());
            if (existingByUsername.isPresent() && !existingByUsername.get().getId().equals(user.getId())) {
                result.rejectValue("username", "error.user", "Ce nom d'utilisateur existe déjà");
            }
            Optional<User> existingByEmail = userRepository.findByEmail(user.getEmail());
            if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(user.getId())) {
                result.rejectValue("email", "error.user", "Cet email existe déjà");
            }
        }

        if (result.hasErrors()) {
            model.addAttribute("isEdit", user.getId() != null);
            List<Role> allRoles = roleRepository.findAll();
            model.addAttribute("allRoles", allRoles);
            return "admin/users/form";
        }

        try {
            // Gestion du mot de passe
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(newPassword));
            } else if (user.getId() != null) {
                // Conserver l'ancien mot de passe
                userRepository.findById(user.getId()).ifPresent(existingUser -> user.setPassword(existingUser.getPassword()));
            }

            // Gestion des rôles
            Set<Role> userRoles = new HashSet<>();
            if (roleIds != null && !roleIds.isEmpty()) {
                for (Long roleId : roleIds) {
                    Optional<Role> roleOpt = roleRepository.findById(roleId);
                    roleOpt.ifPresent(userRoles::add);
                }
            }
            user.setRoles(userRoles);

            // Définir les dates de création/modification
            if (user.getId() == null) {
                user.setCreatedAt(LocalDateTime.now());
            }
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);

            String message = user.getId() != null ? "Utilisateur modifié avec succès" : "Utilisateur créé avec succès";
            redirectAttributes.addFlashAttribute("success", message);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();
            user.setEnabled(!user.isEnabled());
            userRepository.save(user);

            String status = user.isEnabled() ? "activé" : "désactivé";
            redirectAttributes.addFlashAttribute("success", "Utilisateur " + status + " avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors du changement de statut: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetUserPassword(
            @PathVariable Long id,
            @RequestParam String newPassword,
            RedirectAttributes redirectAttributes) {

        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/admin/users";
            }

            if (newPassword == null || newPassword.trim().length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Le mot de passe doit contenir au moins 6 caractères");
                return "redirect:/admin/users/" + id;
            }

            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("success", "Mot de passe réinitialisé avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la réinitialisation: " + e.getMessage());
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();

            // Vérifier qu'on ne supprime pas le dernier admin
            if (user.hasRole("ADMIN")) {
                long adminCount = userRepository.findAll().stream().filter(u -> u.hasRole("ADMIN")).count();
                if (adminCount <= 1) {
                    redirectAttributes.addFlashAttribute("error", "Impossible de supprimer le dernier administrateur");
                    return "redirect:/admin/users/" + id;
                }
            }

            // TODO: Vérifier les dépendances (commandes, clients assignés, etc.)
            // Pour l'instant, on va juste désactiver l'utilisateur
            user.setEnabled(false);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("success", "Utilisateur désactivé avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/impersonate")
    public String impersonateUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // TODO: Implémenter la fonctionnalité d'impersonnation
        redirectAttributes.addFlashAttribute("info", "Fonctionnalité d'impersonnation en cours de développement");
        return "redirect:/admin/users/" + id;
    }
}