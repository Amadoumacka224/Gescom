package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ActivityLogService activityLogService;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            // Don't fail business operation if logging fails
        }
    }

    public List<Invoice> getAllInvoices() {
        List<Invoice> invoices = invoiceRepository.findAll();
        // Recalculate TVA for old invoices that don't have it
        for (Invoice invoice : invoices) {
            if ((invoice.getTaxAmount() == null || invoice.getTaxAmount().compareTo(BigDecimal.ZERO) == 0)
                && invoice.getSubtotal() != null && invoice.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxRate = invoice.getTaxRate() != null && invoice.getTaxRate().compareTo(BigDecimal.ZERO) > 0
                    ? invoice.getTaxRate() : new BigDecimal("20");
                BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
                BigDecimal subtotalAfterDiscount = invoice.getSubtotal().subtract(discount);
                BigDecimal taxAmount = subtotalAfterDiscount.multiply(taxRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                invoice.setTaxRate(taxRate);
                invoice.setTaxAmount(taxAmount);
                invoice.setTotalAmount(subtotalAfterDiscount.add(taxAmount));
                invoice.setRemainingAmount(invoice.getTotalAmount().subtract(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO));
                invoiceRepository.save(invoice);
            }
        }
        return invoices;
    }

    public Optional<Invoice> getInvoiceById(Long id) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            // Force loading of lazy relations
            if (invoice.getOrder() != null) {
                invoice.getOrder().getId(); // Initialize order
                if (invoice.getOrder().getClient() != null) {
                    invoice.getOrder().getClient().getId(); // Initialize client
                }
                if (invoice.getOrder().getItems() != null) {
                    invoice.getOrder().getItems().size(); // Initialize items
                }
            }
            // Recalculate TVA if not set (for old invoices)
            if ((invoice.getTaxAmount() == null || invoice.getTaxAmount().compareTo(BigDecimal.ZERO) == 0)
                && invoice.getSubtotal() != null && invoice.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxRate = invoice.getTaxRate() != null && invoice.getTaxRate().compareTo(BigDecimal.ZERO) > 0
                    ? invoice.getTaxRate() : new BigDecimal("20");
                BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
                BigDecimal subtotalAfterDiscount = invoice.getSubtotal().subtract(discount);
                BigDecimal taxAmount = subtotalAfterDiscount.multiply(taxRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                invoice.setTaxRate(taxRate);
                invoice.setTaxAmount(taxAmount);
                invoice.setTotalAmount(subtotalAfterDiscount.add(taxAmount));
                invoice.setRemainingAmount(invoice.getTotalAmount().subtract(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO));
                // Save the updated invoice
                invoiceRepository.save(invoice);
            }
        }
        return invoiceOpt;
    }

    public Optional<Invoice> getInvoiceByInvoiceNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber);
    }

    public Optional<Invoice> getInvoiceByOrder(Long orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }

    public List<Invoice> getInvoicesByStatus(Invoice.InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }

    public List<Invoice> getInvoicesByDateRange(LocalDate start, LocalDate end) {
        return invoiceRepository.findByInvoiceDateBetween(start, end);
    }

    public List<Invoice> getOverdueInvoices() {
        return invoiceRepository.findByDueDateBeforeAndStatusNot(LocalDate.now(), Invoice.InvoiceStatus.PAID);
    }

    public Invoice createInvoice(Invoice invoice) {
        // Check if order exists and is delivered
        Order order = orderRepository.findById(invoice.getOrder().getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new RuntimeException("Order must be delivered before creating invoice");
        }

        // Calculate invoice amounts
        // Subtotal = montant de la commande (montant HT de base)
        BigDecimal subtotal = order.getTotalAmount();
        invoice.setSubtotal(subtotal);

        // Apply discount first
        BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
        BigDecimal subtotalAfterDiscount = subtotal.subtract(discount);

        // Calculate tax amount from tax rate
        // taxAmount = subtotalAfterDiscount * (taxRate / 100)
        BigDecimal taxRate = invoice.getTaxRate() != null ? invoice.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxAmount = subtotalAfterDiscount.multiply(taxRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        invoice.setTaxAmount(taxAmount);

        // Total = Subtotal - Discount + Tax
        BigDecimal totalAmount = subtotalAfterDiscount.add(taxAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setRemainingAmount(totalAmount.subtract(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO));

        // Determine status based on paid amount
        BigDecimal paidAmount = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        invoice.setPaidAmount(paidAmount);

        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus(Invoice.InvoiceStatus.UNPAID);
        } else if (paidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaymentDate(LocalDate.now());
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Update order status
        order.setStatus(Order.OrderStatus.INVOICED);
        orderRepository.save(order);

        // Log activity
        logActivity(ActivityLog.ActionType.CREATE, "Invoice", savedInvoice.getId(),
            "Création de la facture " + savedInvoice.getInvoiceNumber() + " - Montant: " + savedInvoice.getTotalAmount());

        return savedInvoice;
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod) {
        return recordPayment(id, amount, paymentMethod, LocalDate.now());
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod, LocalDate paymentDate) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already paid");
        }

        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        invoice.setPaymentMethod(paymentMethod);

        if (invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
            invoice.setPaidAmount(invoice.getTotalAmount()); // Cap at total amount
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Log activity
        logActivity(ActivityLog.ActionType.PAYMENT, "Invoice", savedInvoice.getId(),
            "Paiement de " + amount + " sur la facture " + savedInvoice.getInvoiceNumber() + " (" + paymentMethod + ")");

        return savedInvoice;
    }

    public void cancelInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new RuntimeException("Cannot cancel a paid invoice");
        }

        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);
        invoiceRepository.save(invoice);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Invoice", id,
            "Annulation de la facture " + invoice.getInvoiceNumber());
    }

    public void deleteInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        String invoiceNumber = invoice.getInvoiceNumber();
        invoiceRepository.delete(invoice);

        // Log activity
        logActivity(ActivityLog.ActionType.DELETE, "Invoice", id,
            "Suppression de la facture " + invoiceNumber);
    }
}
