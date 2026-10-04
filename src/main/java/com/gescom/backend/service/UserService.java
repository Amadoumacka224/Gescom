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
import com.gescom.backend.mapper.ReferenceMapper;
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
 * Service de gestion des utilisateurs (comptes, rôles, mots de passe).
 * Deux façons de modifier : updateUserAsAdmin (rôle, activation, nouveau mot de passe) et
 * updateSelf (l'utilisateur modifie seulement son propre profil). Les mots de passe sont
 * toujours vérifiés (règles de solidité) puis encodés en BCrypt avant d'être enregistrés.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final ReferenceMapper referenceMapper;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ActivityLogService activityLogService, ReferenceMapper referenceMapper,
                       PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.referenceMapper = referenceMapper;
        this.passwordPolicy = passwordPolicy;
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
     * Vérifie que le mot de passe est assez solide : au moins 8 caractères, une majuscule,
     * une minuscule et un chiffre. Lance une BusinessException dès qu'une règle n'est pas respectée.
     */
    /**
     * Passe par {@link PasswordPolicy}, le seul endroit ou est definie la regle depuis que le
     * back-office plateforme l'utilise aussi. Au passage, le controle du chiffre a maintenant
     * une cle de traduction, qu'il n'avait pas ici (le message etait toujours en francais,
     * peu importe la langue de l'appelant).
     */
    private void validatePassword(String password) {
        passwordPolicy.validate(password);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(referenceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserById(Long id) {
        return userRepository.findById(id).map(referenceMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserByUsername(String username) {
        return userRepository.findByUsername(username).map(referenceMapper::toResponse);
    }

    /**
     * Empêche l'admin d'une entreprise de créer un compte super admin.
     *
     * Sans cette vérification, un ADMIN pourrait se donner le rôle SUPER_ADMIN et accéder à
     * toutes les entreprises : une élévation de privilèges qui passerait de l'entreprise
     * cliente à la gestion du SaaS.
     *
     * La contrainte {@code chk_users_company_scope} refuserait déjà l'écriture en base,
     * puisque le compte aurait une entreprise, mais avec une erreur d'intégrité pas claire du
     * tout. On refuse donc ici pour que ce soit explicite, et pour ne pas laisser une règle de
     * sécurité reposer uniquement sur une contrainte de la base.
     *
     * Le compte super admin se crée uniquement au démarrage, par PlatformAdminBootstrap et les
     * variables d'environnement PLATFORM_ADMIN_*.
     */
    private void rejectPlatformRole(User.Role role) {
        if (role == User.Role.SUPER_ADMIN) {
            throw BusinessException.of("user.role.platformReserved",
                    "Le rôle propriétaire de la plateforme ne peut pas être attribué depuis cet écran");
        }
    }

    public UserResponse createUser(UserCreateRequest request) {
        rejectPlatformRole(request.role());

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

        return referenceMapper.toResponse(savedUser);
    }

    public UserResponse updateUserAsAdmin(Long id, UserUpdateAdminRequest request) {
        rejectPlatformRole(request.role());

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));

        // L'email ne peut pas être utilisé par un autre utilisateur (on ignore l'utilisateur modifié).
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

        return referenceMapper.toResponse(savedUser);
    }

    public UserResponse updateSelf(Long id, UserUpdateSelfRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user", id));

        // L'email ne peut pas être utilisé par un autre utilisateur (on ignore l'utilisateur modifié).
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

        return referenceMapper.toResponse(savedUser);
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
        return userRepository.findByRole(role).stream().map(referenceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getActiveUsers() {
        return userRepository.findByActive(true).stream().map(referenceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getCaissiers() {
        return userRepository.findByRole(User.Role.CAISSIER).stream().map(referenceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAdmins() {
        return userRepository.findByRole(User.Role.ADMIN).stream().map(referenceMapper::toResponse).toList();
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user", userId));

        // Si l'ancien mot de passe est donné, il doit être bon (cas « je change mon propre mot
        // de passe »). Un admin qui réinitialise un compte peut ne pas le donner.
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
