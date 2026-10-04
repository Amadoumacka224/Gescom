package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Parametres de la plateforme, avec les infos du compte super admin.
 *
 * Les deux sont renvoyes ensemble parce que l'ecran Parametres les affiche cote a cote, pas
 * la peine de faire un deuxieme appel pour deux champs. Bien sur, aucun hash de mot de passe
 * n'est renvoye.
 */
public record PlatformSettingsResponse(
        Integer renewalWindowDays,
        Integer trialAlertDays,
        Integer revenueHistoryMonths,
        Integer overduePenaltyPoints,
        Integer failedPaymentPenaltyPoints,
        LocalDateTime updatedAt,
        Account account
) {

    /** Compte super admin connecte. */
    public record Account(
            Long id,
            String username,
            String email,
            String fullName
    ) {
    }
}
