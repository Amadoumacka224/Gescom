package com.gescom.backend.mapper;

import com.gescom.backend.dto.invoice.InvoiceCreateRequest;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InvoiceMapper {

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;

    public InvoiceMapper(OrderMapper orderMapper, OrderRepository orderRepository) {
        this.orderMapper = orderMapper;
        this.orderRepository = orderRepository;
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        if (invoice == null) return null;
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                orderMapper.toResponse(invoice.getOrder()),
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

    public Invoice toEntity(InvoiceCreateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("order", request.orderId()));

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setDueDate(request.dueDate());
        invoice.setPaymentMethod(request.paymentMethod());
        invoice.setTaxRate(request.taxRate() != null ? request.taxRate() : BigDecimal.ZERO);
        invoice.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        invoice.setPaidAmount(request.paidAmount() != null ? request.paidAmount() : BigDecimal.ZERO);
        invoice.setNotes(request.notes());
        return invoice;
    }
}
