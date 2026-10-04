package com.gescom.backend.config;

import com.gescom.backend.tenancy.TenantAwareRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Remplace l'implementation par defaut des repositories Spring Data par
 * {@link TenantAwareRepositoryImpl}.
 *
 * C'est grace a ce reglage que les acces par id verifient l'entreprise : sans lui,
 * {@code findById} renverrait n'importe quelle ligne de la base, filtre Hibernate ou pas.
 *
 * Mettre {@code @EnableJpaRepositories} desactive la config automatique de Spring Boot,
 * il faut donc indiquer le package de base a la main.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.gescom.backend.repository",
        repositoryBaseClass = TenantAwareRepositoryImpl.class
)
public class PersistenceConfig {
}
