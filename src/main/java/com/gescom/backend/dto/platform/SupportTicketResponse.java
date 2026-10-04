package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket renvoye au back-office.
 *
 * {@code messages} est null dans les listes et rempli dans le detail : charger les messages
 * de chaque ligne ne servirait a rien et ferait une requete par ticket.
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
