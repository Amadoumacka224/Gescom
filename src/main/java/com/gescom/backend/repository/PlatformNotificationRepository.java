package com.gescom.backend.repository;

import com.gescom.backend.entity.PlatformNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Historique des evenements de la plateforme. Pas filtre par entreprise, reserve a
 * l'espace super admin.
 */
@Repository
public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, Long> {

    Page<PlatformNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PlatformNotification> findByReadAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** Nombre du badge : utilise l'index partiel sur les non lues. */
    long countByReadAtIsNull();

    /**
     * Marque tout comme lu en une seule requete.
     *
     * Charger puis sauvegarder ligne par ligne ferait un UPDATE par notification, alors que
     * cette action sert justement a vider plusieurs dizaines de notifications d'un coup.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformNotification n SET n.readAt = :now WHERE n.readAt IS NULL")
    int markAllRead(LocalDateTime now);
}
