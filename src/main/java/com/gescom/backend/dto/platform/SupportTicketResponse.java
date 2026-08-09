package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket expose au back-office.
 *
 * {@code messages} est nul sur les listes et renseigne sur le detail : charger le fil de
 * chaque ligne d'une page n'aurait aucun usage et couterait une requete par ticket.
 */
public record SupportTicketResponse(
        Long id,
        String reference,
        Long companyId,
        String companyName,
        String subject,
        String status,
        String priority,
        String category,
        String contactName,
        String openedByName,
        int messageCount,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SupportMessageResponse> messages
) {
}
