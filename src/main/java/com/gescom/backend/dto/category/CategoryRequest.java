package com.gescom.backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Le nom de la catégorie est obligatoire")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 50)
        String code,

        Boolean active
) {
}
