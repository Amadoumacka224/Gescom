package com.gescom.service;

// src/main/java/com/company/commercial/service/UserDetailsServiceImpl.java

import com.gescom.entity.User;
import com.gescom.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Tentative de connexion pour l'utilisateur: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("Utilisateur non trouvé: {}", username);
                    return new UsernameNotFoundException("Utilisateur non trouvé: " + username);
                });

        if (!user.isEnabled()) {
            logger.warn("Tentative de connexion d'un compte désactivé: {}", username);
            throw new UsernameNotFoundException("Compte désactivé: " + username);
        }

        // Mise à jour de la dernière connexion
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        logger.info("Utilisateur connecté avec succès: {} - Rôles: {}",
                username, user.getRoles().stream().map(role -> role.getName()).toList());

        return user;
    }
}