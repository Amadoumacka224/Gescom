package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlatformNotificationResponse;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.PlatformNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Journal des evenements de la plateforme.
 *
 * Complementaire des alertes du tableau de bord, qui decrivent l'etat courant : ici, ce qui
 * s'est passe, avec un etat de lecture.
 */
@RestController
@RequestMapping("/api/platform/notifications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Notifications", description = "Evenements notables de la plateforme")
public class PlatformNotificationController {

    private final PlatformNotificationService notificationService;
    private final PlatformMapper platformMapper;

    public PlatformNotificationController(PlatformNotificationService notificationService,
                                          PlatformMapper platformMapper) {
        this.notificationService = notificationService;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des notifications")
    public ResponseEntity<PageResponse<PlatformNotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<PlatformNotificationResponse> result = notificationService
                .list(unreadOnly, PageRequest.of(page, size))
                .map(platformMapper::toResponse);

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    /** Compteur du badge : appele a chaque affichage de l'interface, servi par un index partiel. */
    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<PlatformNotificationResponse> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(notificationService.markRead(id)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead()));
    }
}
