package com.gescom.backend.repository;

import com.gescom.backend.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Reglages de la plateforme. Table de reference non cloisonnee : le controle d'acces repose
 * sur le {@code @PreAuthorize} de l'espace plateforme.
 */
@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
