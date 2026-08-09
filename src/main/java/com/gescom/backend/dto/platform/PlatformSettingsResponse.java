package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Reglages de la plateforme, accompagnes de l'identite du compte proprietaire.
 *
 * Les deux voyagent ensemble parce que l'ecran Parametres les affiche cote a cote et qu'un
 * second aller-retour pour deux champs n'aurait aucun interet. Aucune empreinte de mot de
 * passe n'y figure, evidemment.
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

    /** Compte proprietaire connecte. */
    public record Account(
            Long id,
            String username,
            String email,
            String fullName
    ) {
    }
}
