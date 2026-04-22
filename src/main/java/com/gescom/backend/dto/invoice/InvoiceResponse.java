package com.gescom.backend.dto.invoice;

import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        OrderResponse order,
        Long deliveryId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxAmount,
        BigDecimal taxRate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        Invoice.InvoiceStatus status,
        Invoice.PaymentMethod paymentMethod,
        LocalDate paymentDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        if (invoice == null) return null;
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                OrderResponse.from(invoice.getOrder()),
                invoice.getDelivery() != null ? invoice.getDelivery().getId() : null,
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getSubtotal(),
                invoice.getDiscount(),
                invoice.getTaxAmount(),
                invoice.getTaxRate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getRemainingAmount(),
                invoice.getStatus(),
                invoice.getPaymentMethod(),
                invoice.getPaymentDate(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
