package com.gescom.backend.dto.activity;

import com.gescom.backend.entity.ActivityLog;

import java.util.List;

/**
 * Indicateurs de l'écran Historique, agrégés en base sur la totalité du journal.
 *
 * Existe parce que la liste est paginée : compter les lignes reçues ne donnerait que le
 * volume de la page courante, et un total qui n'en est pas un est plus trompeur qu'absent.
 * Les listes `actionTypes` / `entities` recensent les valeurs réellement présentes, afin de
 * ne proposer aucun filtre qui ne rendrait aucun résultat.
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
