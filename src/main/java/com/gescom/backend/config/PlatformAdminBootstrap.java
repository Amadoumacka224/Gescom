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
 * Cree le compte super admin au premier demarrage, a partir de variables d'environnement.
 *
 * On ne le cree pas dans une migration Flyway exprès : il faudrait y mettre un hash de mot
 * de passe en dur, versionne et le meme sur toutes les installations. C'est typiquement le
 * mot de passe par defaut que personne ne change jamais. Ici on encode avec le
 * {@code PasswordEncoder} du projet un mot de passe fourni a l'installation, comme
 * {@code DB_PASSWORD} ou {@code JWT_SECRET}.
 *
 * Si les variables ne sont pas definies, rien n'est cree et l'application demarre
 * normalement : on n'est pas oblige d'avoir un back-office plateforme.
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
            // On ne réécrit jamais le mot de passe au redémarrage, sinon un changement fait
            // depuis l'application serait écrasé au déploiement suivant.
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
        // Pas d'entreprise : c'est ce qui donne la vue sur toutes les entreprises, et la
        // contrainte chk_users_company_scope l'impose en base pour ce rôle.
        owner.setOwnerCompany(null);
        userRepository.save(owner);

        log.info("Compte propriétaire de la plateforme « {} » créé.", username);
    }
}
