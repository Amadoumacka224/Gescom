package com.gescom.service;

// src/main/java/com/company/commercial/service/UserService.java

import com.gescom.entity.Role;
import com.gescom.entity.User;
import com.gescom.repository.RoleRepository;
import com.gescom.repository.UserRepository;
import com.gescom.exception.UserAlreadyExistsException;
import com.gescom.exception.UserNotFoundException;
import com.gescom.entity.User;
import com.gescom.exception.UserAlreadyExistsException;
import com.gescom.repository.RoleRepository;
import com.gescom.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private UserRepository userRepository;

    private RoleRepository roleRepository;

    private PasswordEncoder passwordEncoder;

    /**
     * Créer un nouvel utilisateur
     */
    public User createUser(User user) {
        logger.info("Création d'un nouvel utilisateur: {}", user.getUsername());

        // Vérifier l'unicité du username et email
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new UserAlreadyExistsException("Un utilisateur avec ce nom d'utilisateur existe déjà");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Un utilisateur avec cet email existe déjà");
        }

        // Encoder le mot de passe
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Assigner le rôle USER par défaut s'il n'y a pas de rôle
        if (user.getRoles().isEmpty()) {
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("Rôle USER non trouvé"));
            user.addRole(userRole);
        }

        User savedUser = userRepository.save(user);
        logger.info("Utilisateur créé avec succès: {} (ID: {})", savedUser.getUsername(), savedUser.getId());

        return savedUser;
    }

    /**
     * Mettre à jour un utilisateur existant
     */
    public User updateUser(Long userId, User updatedUser) {
        logger.info("Mise à jour de l'utilisateur ID: {}", userId);

        User existingUser = findById(userId);

        // Vérifier l'unicité si le username/email a changé
        if (!existingUser.getUsername().equals(updatedUser.getUsername()) &&
                userRepository.existsByUsername(updatedUser.getUsername())) {
            throw new UserAlreadyExistsException("Un utilisateur avec ce nom d'utilisateur existe déjà");
        }

        if (!existingUser.getEmail().equals(updatedUser.getEmail()) &&
                userRepository.existsByEmail(updatedUser.getEmail())) {
            throw new UserAlreadyExistsException("Un utilisateur avec cet email existe déjà");
        }

        // Mise à jour des champs
        existingUser.setUsername(updatedUser.getUsername());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());
        existingUser.setEnabled(updatedUser.isEnabled());

        // Mise à jour du mot de passe si fourni
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }

        User savedUser = userRepository.save(existingUser);
        logger.info("Utilisateur mis à jour avec succès: {}", savedUser.getUsername());

        return savedUser;
    }

    /**
     * Trouver un utilisateur par ID
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Utilisateur non trouvé avec l'ID: " + id));
    }

    /**
     * Trouver un utilisateur par nom d'utilisateur
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Trouver un utilisateur par email
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Obtenir tous les utilisateurs
     */
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * Rechercher des utilisateurs
     */
    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword) {
        return userRepository.searchUsers(keyword);
    }

    /**
     * Obtenir les utilisateurs par rôle
     */
    @Transactional(readOnly = true)
    public List<User> findByRoleName(String roleName) {
        return userRepository.findByRoleName(roleName);
    }

    /**
     * Désactiver un utilisateur
     */
    public void disableUser(Long userId) {
        logger.info("Désactivation de l'utilisateur ID: {}", userId);

        User user = findById(userId);
        user.setEnabled(false);
        userRepository.save(user);

        logger.info("Utilisateur désactivé: {}", user.getUsername());
    }

    /**
     * Activer un utilisateur
     */
    public void enableUser(Long userId) {
        logger.info("Activation de l'utilisateur ID: {}", userId);

        User user = findById(userId);
        user.setEnabled(true);
        userRepository.save(user);

        logger.info("Utilisateur activé: {}", user.getUsername());
    }

    /**
     * Supprimer un utilisateur
     */
    public void deleteUser(Long userId) {
        logger.info("Suppression de l'utilisateur ID: {}", userId);

        User user = findById(userId);
        userRepository.delete(user);

        logger.info("Utilisateur supprimé: {}", user.getUsername());
    }

    /**
     * Changer le mot de passe d'un utilisateur
     */
    public void changePassword(Long userId, String newPassword) {
        logger.info("Changement de mot de passe pour l'utilisateur ID: {}", userId);

        User user = findById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Mot de passe changé pour l'utilisateur: {}", user.getUsername());
    }

    /**
     * Assigner un rôle à un utilisateur
     */
    public void assignRole(Long userId, String roleName) {
        logger.info("Attribution du rôle {} à l'utilisateur ID: {}", roleName, userId);

        User user = findById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Rôle non trouvé: " + roleName));

        user.addRole(role);
        userRepository.save(user);

        logger.info("Rôle {} attribué à l'utilisateur {}", roleName, user.getUsername());
    }

    /**
     * Retirer un rôle d'un utilisateur
     */
    public void removeRole(Long userId, String roleName) {
        logger.info("Retrait du rôle {} de l'utilisateur ID: {}", roleName, userId);

        User user = findById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Rôle non trouvé: " + roleName));

        user.removeRole(role);
        userRepository.save(user);

        logger.info("Rôle {} retiré de l'utilisateur {}", roleName, user.getUsername());
    }

    /**
     * Obtenir les statistiques utilisateurs
     */
    @Transactional(readOnly = true)
    public UserStats getUserStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countActiveUsers();
        long inactiveUsers = totalUsers - activeUsers;

        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        long newUsersThisMonth = userRepository.findByCreatedAtBetween(oneMonthAgo, LocalDateTime.now()).size();

        return new UserStats(totalUsers, activeUsers, inactiveUsers, newUsersThisMonth);
    }

    /**
     * Classe pour les statistiques utilisateurs
     */
    public static class UserStats {
        private final long totalUsers;
        private final long activeUsers;
        private final long inactiveUsers;
        private final long newUsersThisMonth;

        public UserStats(long totalUsers, long activeUsers, long inactiveUsers, long newUsersThisMonth) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.inactiveUsers = inactiveUsers;
            this.newUsersThisMonth = newUsersThisMonth;
        }

        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getActiveUsers() { return activeUsers; }
        public long getInactiveUsers() { return inactiveUsers; }
        public long getNewUsersThisMonth() { return newUsersThisMonth; }
    }
}