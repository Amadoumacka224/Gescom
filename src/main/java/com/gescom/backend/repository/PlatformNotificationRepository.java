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
 * Journal des evenements de la plateforme. Table non cloisonnee, reservee a l'espace
 * proprietaire.
 */
@Repository
public interface PlatformNotificationRepository extends JpaRepository<PlatformNotification, Long> {

    Page<PlatformNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PlatformNotification> findByReadAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** Compteur du badge : sert l'index partiel pose sur les seules non-lues. */
    long countByReadAtIsNull();

    /**
     * Marque tout comme lu en une requete.
     *
     * Charger puis sauver ligne par ligne ferait autant d'UPDATE que de notifications, sur
     * une action qui vise justement a solder un arriere de plusieurs dizaines d'entrees.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformNotification n SET n.readAt = :now WHERE n.readAt IS NULL")
    int markAllRead(LocalDateTime now);
}
