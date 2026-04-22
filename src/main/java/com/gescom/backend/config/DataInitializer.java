package com.gescom.backend.config;

import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("========================================");
            log.info("Aucun utilisateur trouvé - Création du super admin");
            log.info("========================================");

            User superAdmin = new User();
            superAdmin.setUsername("admin");
            superAdmin.setEmail("admin@gescom.com");
            superAdmin.setPassword(passwordEncoder.encode("Admin@2024"));
            superAdmin.setFirstName("Super");
            superAdmin.setLastName("Admin");
            superAdmin.setPhone("+213 000 000 000");
            superAdmin.setRole(User.Role.ADMIN);
            superAdmin.setActive(true);

            userRepository.save(superAdmin);

            log.info("========================================");
            log.info("Super Admin créé avec succès!");
            log.info("   Username: admin");
            log.info("   Password: Admin@2024");
            log.info("   Email: admin@gescom.com");
            log.info("========================================");
            log.warn("IMPORTANT: Changez ce mot de passe après la première connexion!");
            log.info("========================================");
        } else {
            log.info("Base de données déjà initialisée - {} utilisateur(s) trouvé(s)", userRepository.count());
        }
    }
}
