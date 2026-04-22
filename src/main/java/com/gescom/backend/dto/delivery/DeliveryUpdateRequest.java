package com.gescom.backend.dto.delivery;

import com.gescom.backend.entity.Delivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DeliveryUpdateRequest(
        @NotBlank(message = "L'adresse de livraison est obligatoire")
        @Size(max = 255)
        String deliveryAddress,

        @Size(max = 100)
        String deliveryCity,

        @Size(max = 20)
        String deliveryPostalCode,

        @Size(max = 100)
        String deliveryCountry,

        @NotBlank(message = "Le nom du contact est obligatoire")
        @Size(max = 100)
        String contactName,

        @NotBlank(message = "Le téléphone du contact est obligatoire")
        @Size(max = 20)
        String contactPhone,

        @NotNull(message = "La date planifiée est obligatoire")
        LocalDateTime scheduledDate,

        Delivery.DeliveryStatus status,

        @Size(max = 500)
        String notes
) {
}
