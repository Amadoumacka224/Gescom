package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

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
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new BusinessException("Le mot de passe ne peut pas être vide");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("Le mot de passe doit contenir au moins une lettre majuscule");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("Le mot de passe doit contenir au moins une lettre minuscule");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Le mot de passe doit contenir au moins un chiffre");
        }
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("Utilisateur", "username", user.getUsername());
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("Utilisateur", "email", user.getEmail());
        }

        String password = user.getRawPassword();
        if (password == null || password.isEmpty()) {
            password = user.getPassword();
        }

        if (password == null || password.isEmpty()) {
            throw new BusinessException("Le mot de passe est obligatoire");
        }

        validatePassword(password);
        user.setPassword(passwordEncoder.encode(password));
        User savedUser = userRepository.save(user);

        logActivity(ActivityLog.ActionType.CREATE, "User", savedUser.getId(),
            "Création de l'utilisateur " + savedUser.getUsername() + " (" + savedUser.getRole() + ")");

        return savedUser;
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setEmail(userDetails.getEmail());
        user.setPhone(userDetails.getPhone());
        user.setRole(userDetails.getRole());
        user.setActive(userDetails.getActive());

        String password = userDetails.getRawPassword();
        if (password == null || password.isEmpty()) {
            password = userDetails.getPassword();
        }

        if (password != null && !password.isEmpty()) {
            validatePassword(password);
            user.setPassword(passwordEncoder.encode(password));
        }

        User savedUser = userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", savedUser.getId(),
            "Modification de l'utilisateur " + savedUser.getUsername());

        return savedUser;
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        String username = user.getUsername();
        userRepository.delete(user);

        logActivity(ActivityLog.ActionType.DELETE, "User", id,
            "Suppression de l'utilisateur " + username);
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setActive(false);
        userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", id,
            "Désactivation de l'utilisateur " + user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<User> getUsersByRole(User.Role role) {
        return userRepository.findByRole(role);
    }

    @Transactional(readOnly = true)
    public List<User> getActiveUsers() {
        return userRepository.findByActive(true);
    }

    @Transactional(readOnly = true)
    public List<User> getCaissiers() {
        return userRepository.findByRole(User.Role.CAISSIER);
    }

    @Transactional(readOnly = true)
    public List<User> getAdmins() {
        return userRepository.findByRole(User.Role.ADMIN);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (currentPassword != null && !currentPassword.isEmpty()) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new BusinessException("Mot de passe actuel incorrect");
            }
        }

        validatePassword(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", userId,
            "Changement de mot de passe pour l'utilisateur " + user.getUsername());
    }
}
