package com.gescom.backend.dto.activity;

import com.gescom.backend.entity.ActivityLog;

import java.util.List;

/**
 * Stats de l'écran Historique, calculées en base sur tout le journal.
 *
 * Nécessaire parce que la liste est paginée : compter les lignes reçues ne donnerait que
 * celles de la page, et un faux total est pire que pas de total.
 * `actionTypes` / `entities` listent les valeurs qui existent vraiment, pour ne pas proposer
 * de filtre qui ne donnerait rien.
 */
public record ActivityLogSummary(
        long total,
        long today,
        long week,
        long activeUsersToday,
        long deletions,
        List<ActivityLog.ActionType> actionTypes,
        List<String> entities
) {
}
