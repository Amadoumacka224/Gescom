package com.gescom.backend.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Format des réponses paginées.
 *
 * On utilise notre propre DTO plutôt que le {@code Page} de Spring Data : sa sérialisation
 * JSON change d'une version à l'autre (Spring affiche d'ailleurs un warning au démarrage).
 * On renvoie donc juste ce qu'il faut pour un tableau paginé.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Convertit une page d'entités en page de DTO avec le mapper donné. */
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
