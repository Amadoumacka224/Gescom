package com.gescom.backend.service;

import com.gescom.backend.dto.user.ChangePasswordRequest;
import com.gescom.backend.dto.user.UserCreateRequest;
import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.dto.user.UserUpdateAdminRequest;
import com.gescom.backend.dto.user.UserUpdateSelfRequest;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.UserMapper;
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

/**
 * Service métier de gestion des utilisateurs (comptes, rôles, mots de passe).
 * Distingue deux niveaux de mise à jour : updateUserAsAdmin (rôle, activation, reset mot de passe)
 * et updateSelf (l'utilisateur édite uniquement son propre profil). Les mots de passe sont
 * toujours validés (politique de robustesse) puis encodés en BCrypt avant persistance.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ActivityLogService activityLogService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.userMapper = userMapper;
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

    /**
     * Applique la politique de robustesse des mots de passe : au moins 8 caractères,
     * une majuscule, une minuscule et un chiffre. Lève BusinessException au premier critère manqué.
     */
    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw BusinessException.of("user.password.empty", "Le mot de passe ne peut pas être vide");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw BusinessException.of("user.password.tooShort",
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères",
                    MIN_PASSWORD_LENGTH);
        }
        if (!password.matches(".*[A-Z].*")) {
            throw BusinessException.of("user.password.needsUppercase",
                    "Le mot de passe doit contenir au moins une lettre majuscule");
        }
        if (!password.matches(".*[a-z].*")) {
            throw BusinessException.of("user.password.needsLowercase",
                    "Le mot de passe doit contenir au moins une lettre minuscule");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Le mot de passe doit contenir au moins un chiffre");
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserById(Long id) {
        return userRepository.findById(id).map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserByUsername(String username) {
        return userRepository.findByUsername(username).map(userMapper::toResponse);
    }

    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("user", "username", request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("user", "email", request.email());
        }

        validatePassword(request.password());

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(request.active() != null ? request.active() : true);

        User savedUser = userRepository.save(user);

        logActivity(ActivityLog.ActionType.CREATE, "User", savedUser.getId(),
            "Création de l'utilisateur " + savedUser.getUsername() + " (" + savedUser.getRole() + ")");

        return userMapper.toResponse(savedUser);
    }

    public UserResponse updateUserAsAdmin(Long id, UserUpdateAdminRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));

        // L'email ne peut pas être repris par un autre utilisateur (l'enregistrement courant est exclu).
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("user", "email", request.email());
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(request.active());

        if (request.password() != null && !request.password().isEmpty()) {
            validatePassword(request.password());
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        User savedUser = userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", savedUser.getId(),
            "Modification de l'utilisateur " + savedUser.getUsername());

        return userMapper.toResponse(savedUser);
    }

    public UserResponse updateSelf(Long id, UserUpdateSelfRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));

        // L'email ne peut pas être repris par un autre utilisateur (l'enregistrement courant est exclu).
        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("user", "email", request.email());
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());

        User savedUser = userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", savedUser.getId(),
            "Modification du profil par " + savedUser.getUsername());

        return userMapper.toResponse(savedUser);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));
        String username = user.getUsername();
        userRepository.delete(user);

        logActivity(ActivityLog.ActionType.DELETE, "User", id,
            "Suppression de l'utilisateur " + username);
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));
        user.setActive(false);
        userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", id,
            "Désactivation de l'utilisateur " + user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(User.Role role) {
        return userRepository.findByRole(role).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getActiveUsers() {
        return userRepository.findByActive(true).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getCaissiers() {
        return userRepository.findByRole(User.Role.CAISSIER).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAdmins() {
        return userRepository.findByRole(User.Role.ADMIN).stream().map(userMapper::toResponse).toList();
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId));

        // Si l'ancien mot de passe est fourni, il doit correspondre (cas « je change mon propre
        // mot de passe »). Un admin réinitialisant un compte peut l'omettre.
        if (request.currentPassword() != null && !request.currentPassword().isEmpty()) {
            if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new BusinessException("Mot de passe actuel incorrect");
            }
        }

        validatePassword(request.newPassword());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        logActivity(ActivityLog.ActionType.UPDATE, "User", userId,
            "Changement de mot de passe pour l'utilisateur " + user.getUsername());
    }
}
