package com.gescom.backend.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination des réponses de liste.
 *
 * DTO dédié plutôt que le {@code Page} de Spring Data : la sérialisation Jackson de ce dernier
 * n'est pas stable d'une version à l'autre (et Spring en émet un avertissement au démarrage).
 * On expose donc un contrat explicite et minimal, suffisant pour un tableau paginé.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Convertit une page d'entités en page de DTOs via le mapper fourni. */
    public static <E, R> PageResponse<R> of(Page<E> page, Function<E, R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
