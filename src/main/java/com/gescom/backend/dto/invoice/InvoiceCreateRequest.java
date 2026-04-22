package com.gescom.backend.dto.invoice;

import com.gescom.backend.entity.Invoice;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceCreateRequest(
        @NotNull(message = "L'identifiant de la commande est obligatoire")
        Long orderId,

        @NotNull(message = "La date de facture est obligatoire")
        LocalDate invoiceDate,

        @NotNull(message = "La date d'échéance est obligatoire")
        LocalDate dueDate,

        @NotNull(message = "Le mode de paiement est obligatoire")
        Invoice.PaymentMethod paymentMethod,

        @DecimalMin(value = "0.0", inclusive = true, message = "Le taux de taxe doit être positif ou nul")
        BigDecimal taxRate,

        @DecimalMin(value = "0.0", inclusive = true, message = "La remise doit être positive ou nulle")
        BigDecimal discount,

        @DecimalMin(value = "0.0", inclusive = true, message = "Le montant payé doit être positif ou nul")
        BigDecimal paidAmount,

        @Size(max = 500)
        String notes
) {
}
