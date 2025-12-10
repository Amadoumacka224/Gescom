package com.gescom.backend.config;

import com.gescom.backend.entity.User;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Vérifier si un super admin existe déjà
        if (userRepository.count() == 0) {
            System.out.println("========================================");
            System.out.println("Aucun utilisateur trouvé - Création du super admin");
            System.out.println("========================================");

            // Créer le super admin par défaut
            User superAdmin = new User();
            superAdmin.setUsername("admin");
            superAdmin.setEmail("admin@gescom.com");
            superAdmin.setPassword(passwordEncoder.encode("admin123"));
            superAdmin.setFirstName("Super");
            superAdmin.setLastName("Admin");
            superAdmin.setPhone("+213 000 000 000");
            superAdmin.setRole(User.Role.ADMIN);
            superAdmin.setActive(true);

            userRepository.save(superAdmin);

            System.out.println("========================================");
            System.out.println("✅ Super Admin créé avec succès!");
            System.out.println("   Username: admin");
            System.out.println("   Password: admin123");
            System.out.println("   Email: admin@gescom.com");
            System.out.println("========================================");
            System.out.println("⚠️  IMPORTANT: Changez ce mot de passe après la première connexion!");
            System.out.println("========================================");
        } else {
            System.out.println("Base de données déjà initialisée - " + userRepository.count() + " utilisateur(s) trouvé(s)");
        }
    }
}
