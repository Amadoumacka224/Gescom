package com.gescom.backend.dto.order;

import com.gescom.backend.entity.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record OrderUpdateRequest(
        @NotEmpty(message = "La commande doit contenir au moins un article")
        @Valid
        List<OrderItemRequest> items,

        Order.OrderStatus status,

        @DecimalMin(value = "0.0", inclusive = true, message = "La remise doit être positive ou nulle")
        BigDecimal discount,

        @DecimalMin(value = "0.0", inclusive = true, message = "La taxe doit être positive ou nulle")
        BigDecimal tax,

        @Size(max = 500)
        String notes
) {
}
