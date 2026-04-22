package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ActivityLogService activityLogService;
    private final OrderService orderService;

    public InvoiceService(InvoiceRepository invoiceRepository, OrderRepository orderRepository,
                          ActivityLogService activityLogService, OrderService orderService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.activityLogService = activityLogService;
        this.orderService = orderService;
    }

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
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceById(Long id) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(id);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            // Force loading of lazy relations
            if (invoice.getOrder() != null) {
                invoice.getOrder().getId();
                if (invoice.getOrder().getClient() != null) {
                    invoice.getOrder().getClient().getId();
                }
                if (invoice.getOrder().getItems() != null) {
                    invoice.getOrder().getItems().forEach(item -> {
                        if (item.getProduct() != null) {
                            item.getProduct().getName();
                        }
                    });
                }
            }
        }
        return invoiceOpt;
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceByInvoiceNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber);
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceByOrder(Long orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByStatus(Invoice.InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByDateRange(LocalDate start, LocalDate end) {
        return invoiceRepository.findByInvoiceDateBetween(start, end);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getOverdueInvoices() {
        return invoiceRepository.findByDueDateBeforeAndStatusNot(LocalDate.now(), Invoice.InvoiceStatus.PAID);
    }

    public Invoice createInvoice(Invoice invoice) {
        Order order = orderRepository.findById(invoice.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande", invoice.getOrder().getId()));

        // La facturation est possible depuis CONFIRMED (facture avant livraison)
        // ou depuis DELIVERED (facture après livraison)
        if (order.getStatus() != Order.OrderStatus.CONFIRMED && order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new BusinessException("La commande doit être confirmée ou livrée pour être facturée (statut actuel : " + order.getStatus() + ")");
        }

        // Vérifier qu'une facture n'existe pas déjà pour cette commande
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BusinessException("Une facture existe déjà pour cette commande");
        }

        BigDecimal subtotal = order.getTotalAmount();
        invoice.setSubtotal(subtotal);

        BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
        BigDecimal subtotalAfterDiscount = subtotal.subtract(discount);

        BigDecimal taxRate = invoice.getTaxRate() != null ? invoice.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxAmount = subtotalAfterDiscount.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        invoice.setTaxAmount(taxAmount);

        BigDecimal totalAmount = subtotalAfterDiscount.add(taxAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setRemainingAmount(totalAmount.subtract(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO));

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

        // Transition du statut de la commande (machine à états centralisée dans OrderService) :
        // CONFIRMED → INVOICED (en attente de livraison)
        // DELIVERED → COMPLETED (déjà livrée + maintenant facturée = terminée)
        Order.OrderStatus target = order.getStatus() == Order.OrderStatus.DELIVERED
                ? Order.OrderStatus.COMPLETED
                : Order.OrderStatus.INVOICED;
        orderService.transitionTo(order, target);

        logActivity(ActivityLog.ActionType.CREATE, "Invoice", savedInvoice.getId(),
            "Création de la facture " + savedInvoice.getInvoiceNumber() + " - Montant: " + savedInvoice.getTotalAmount());

        return savedInvoice;
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod) {
        return recordPayment(id, amount, paymentMethod, LocalDate.now());
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod, LocalDate paymentDate) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new BusinessException("La facture est déjà payée");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Le montant du paiement doit être positif");
        }

        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        invoice.setPaymentMethod(paymentMethod);

        if (invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
            invoice.setPaidAmount(invoice.getTotalAmount());
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);

        logActivity(ActivityLog.ActionType.PAYMENT, "Invoice", savedInvoice.getId(),
            "Paiement de " + amount + " sur la facture " + savedInvoice.getInvoiceNumber() + " (" + paymentMethod + ")");

        return savedInvoice;
    }

    public void cancelInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new BusinessException("Impossible d'annuler une facture déjà payée");
        }

        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);
        invoiceRepository.save(invoice);

        logActivity(ActivityLog.ActionType.UPDATE, "Invoice", id,
            "Annulation de la facture " + invoice.getInvoiceNumber());
    }

    public void deleteInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        String invoiceNumber = invoice.getInvoiceNumber();
        invoiceRepository.delete(invoice);

        logActivity(ActivityLog.ActionType.DELETE, "Invoice", id,
            "Suppression de la facture " + invoiceNumber);
    }
}
