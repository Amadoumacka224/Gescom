package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityLogService activityLogService;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            // Don't fail business operation if logging fails
        }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Use rawPassword if provided, otherwise use password
        String password = user.getRawPassword();
        if (password == null || password.isEmpty()) {
            password = user.getPassword();
        }

        if (password == null || password.isEmpty()) {
            throw new RuntimeException("rawPassword cannot be null");
        }

        user.setPassword(passwordEncoder.encode(password));
        User savedUser = userRepository.save(user);

        // Log activity
        logActivity(ActivityLog.ActionType.CREATE, "User", savedUser.getId(),
            "Création de l'utilisateur " + savedUser.getUsername() + " (" + savedUser.getRole() + ")");

        return savedUser;
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setEmail(userDetails.getEmail());
        user.setPhone(userDetails.getPhone());
        user.setRole(userDetails.getRole());
        user.setActive(userDetails.getActive());

        // Use rawPassword if provided, otherwise use password
        String password = userDetails.getRawPassword();
        if (password == null || password.isEmpty()) {
            password = userDetails.getPassword();
        }

        if (password != null && !password.isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }

        User savedUser = userRepository.save(user);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "User", savedUser.getId(),
            "Modification de l'utilisateur " + savedUser.getUsername());

        return savedUser;
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String username = user.getUsername();
        userRepository.delete(user);

        // Log activity
        logActivity(ActivityLog.ActionType.DELETE, "User", id,
            "Suppression de l'utilisateur " + username);
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "User", id,
            "Désactivation de l'utilisateur " + user.getUsername());
    }

    public List<User> getUsersByRole(User.Role role) {
        return userRepository.findByRole(role);
    }

    public List<User> getActiveUsers() {
        return userRepository.findByActive(true);
    }

    public List<User> getCaissiers() {
        return userRepository.findByRole(User.Role.CAISSIER);
    }

    public List<User> getAdmins() {
        return userRepository.findByRole(User.Role.ADMIN);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // If currentPassword is provided, verify it (for non-admin users changing their own password)
        if (currentPassword != null && !currentPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new RuntimeException("Mot de passe actuel incorrect");
            }
        }

        // Validate new password
        if (newPassword == null || newPassword.isEmpty()) {
            throw new RuntimeException("Le nouveau mot de passe ne peut pas être vide");
        }

        if (newPassword.length() < 4) {
            throw new RuntimeException("Le mot de passe doit contenir au moins 4 caractères");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "User", userId,
            "Changement de mot de passe pour l'utilisateur " + user.getUsername());
    }
}
