package com.gescom.backend.repository;

import com.gescom.backend.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Parametres de la plateforme. Pas filtres par entreprise : l'acces est controle par le
 * {@code @PreAuthorize} de l'espace plateforme.
 */
@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
