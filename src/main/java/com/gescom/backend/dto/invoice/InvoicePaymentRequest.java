package com.gescom.backend.dto.invoice;

import com.gescom.backend.entity.Invoice;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoicePaymentRequest(
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.01", inclusive = true, message = "Le montant doit être strictement positif")
        BigDecimal amount,

        @NotNull(message = "Le mode de paiement est obligatoire")
        Invoice.PaymentMethod paymentMethod,

        LocalDate paymentDate
) {
}
