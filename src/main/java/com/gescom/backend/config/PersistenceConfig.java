package com.gescom.backend.config;

import com.gescom.backend.tenancy.TenantAwareRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Substitue {@link TenantAwareRepositoryImpl} a l'implementation par defaut de Spring Data
 * pour l'ensemble des repositories.
 *
 * C'est ce reglage, et lui seul, qui applique le controle d'appartenance aux acces par
 * identifiant : sans lui, {@code findById} continuerait de servir n'importe quelle ligne
 * de la base, filtre Hibernate ou non.
 *
 * Declarer {@code @EnableJpaRepositories} desactive l'auto-configuration equivalente de
 * Spring Boot ; le package de base doit donc etre indique explicitement.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.gescom.backend.repository",
        repositoryBaseClass = TenantAwareRepositoryImpl.class
)
public class PersistenceConfig {
}
