package com.gescom.backend.config;

import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cree le compte du proprietaire de la plateforme au premier demarrage, a partir de
 * variables d'environnement.
 *
 * Ce compte n'est deliberement pas seme par une migration Flyway : il faudrait y inscrire
 * une empreinte de mot de passe en dur, versionnee et identique sur toutes les installations
 * — soit exactement le genre d'identifiant par defaut qui finit par ne jamais etre change.
 * Le faire ici permet d'encoder avec le {@code PasswordEncoder} du projet un secret fourni
 * par l'exploitant, au meme titre que {@code DB_PASSWORD} ou {@code JWT_SECRET}.
 *
 * Sans les variables, rien n'est cree et l'application demarre normalement : une
 * installation qui n'a pas encore besoin du back-office proprietaire n'y est pas contrainte.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.admin.username:}")
    private String username;

    @Value("${platform.admin.email:}")
    private String email;

    @Value("${platform.admin.password:}")
    private String password;

    public PlatformAdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            log.info("Aucun compte propriétaire configuré (PLATFORM_ADMIN_*) : création ignorée.");
            return;
        }
        if (userRepository.existsByUsername(username)) {
            // Idempotent : le mot de passe n'est jamais réécrit au redémarrage, sans quoi
            // une rotation faite depuis l'application serait annulée au prochain déploiement.
            log.debug("Compte propriétaire « {} » déjà présent.", username);
            return;
        }

        User owner = new User();
        owner.setUsername(username);
        owner.setEmail(email);
        owner.setPassword(passwordEncoder.encode(password));
        owner.setFirstName("Propriétaire");
        owner.setLastName("GESCOM");
        owner.setRole(User.Role.SUPER_ADMIN);
        owner.setActive(true);
        // Aucune entreprise : c'est ce rattachement vide qui ouvre la vue globale du parc,
        // et la contrainte chk_users_company_scope en base le rend obligatoire pour ce rôle.
        owner.setOwnerCompany(null);
        userRepository.save(owner);

        log.info("Compte propriétaire de la plateforme « {} » créé.", username);
    }
}
