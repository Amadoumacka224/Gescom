package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.security.CashierScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service métier des factures : émission, enregistrement des paiements et annulation.
 * Maillon central du cycle de vie d'une commande — facturer fait passer l'Order de
 * CONFIRMED à INVOICED (pré-requis pour la livraison). Le statut de la facture
 * (UNPAID / PARTIALLY_PAID / PAID) est déduit du rapport montant payé / montant total.
 */
@Service
@Transactional
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ActivityLogService activityLogService;
    private final OrderService orderService;
    private final CashierScope cashierScope;

    public InvoiceService(InvoiceRepository invoiceRepository, OrderRepository orderRepository,
                          ActivityLogService activityLogService, OrderService orderService,
                          CashierScope cashierScope) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.activityLogService = activityLogService;
        this.orderService = orderService;
        this.cashierScope = cashierScope;
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
        // Ordre déterministe (facture la plus récente en tête) + chargement de la commande
        // associée en une seule requête pour éviter le N+1 au mapping (chaque réponse embarque
        // un OrderResponse complet).
        return invoiceRepository.findAllWithDetails(cashierScope.restrictedUserId());
    }

    /**
     * Lecture unitaire. Hors périmètre du caissier, la facture est rendue absente (404) plutôt
     * que refusée — même règle que pour la vente dont elle découle.
     */
    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceById(Long id) {
        Optional<Invoice> invoiceOpt = cashierScope.filterReadable(
                invoiceRepository.findById(id), Invoice::getOrder);
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
        return cashierScope.filterReadable(
                invoiceRepository.findByInvoiceNumber(invoiceNumber), Invoice::getOrder);
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceByOrder(Long orderId) {
        return cashierScope.filterReadable(invoiceRepository.findByOrderId(orderId), Invoice::getOrder);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByStatus(Invoice.InvoiceStatus status) {
        return invoiceRepository.findByStatus(status, cashierScope.restrictedUserId());
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByDateRange(LocalDate start, LocalDate end) {
        return invoiceRepository.findByInvoiceDateBetween(start, end, cashierScope.restrictedUserId());
    }

    @Transactional(readOnly = true)
    public List<Invoice> getOverdueInvoices() {
        return invoiceRepository.findByDueDateBeforeAndStatusNot(
                LocalDate.now(), Invoice.InvoiceStatus.PAID, cashierScope.restrictedUserId());
    }

    // ── Agrégats du tableau de bord caisse (filtrés sur Order.createdBy) ──────────

    @Transactional(readOnly = true)
    public BigDecimal getCollectedByCashierOnDate(Long userId, LocalDate date) {
        return invoiceRepository.sumCollectedByCashierOnDate(userId, date);
    }

    /**
     * Encaissé du jour ventilé par caissier. Les caissiers sans encaissement sont absents
     * de la map — l'appelant retombe sur zéro.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCollectedPerCashierOnDate(LocalDate date) {
        return invoiceRepository.sumCollectedPerCashierOnDate(date).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));
    }

    /**
     * Facture (s'il y en a une) de chaque commande passée, en une seule requête.
     *
     * Elle porte deux informations que la commande seule ne peut pas donner : son statut — d'où
     * le « Payée » d'une commande facturée puis soldée, dont le statut reste INVOICED jusqu'à la
     * livraison — et son total TTC, le seul montant réellement réclamé au client.
     */
    @Transactional(readOnly = true)
    public Map<Long, Invoice> getInvoicesByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return invoiceRepository.findByOrderIdIn(orderIds).stream()
                .filter(inv -> inv.getOrder() != null)
                .collect(Collectors.toMap(inv -> inv.getOrder().getId(), inv -> inv, (a, b) -> a));
    }

    /** Statuts seuls, pour les appelants qui n'ont pas besoin des montants. */
    @Transactional(readOnly = true)
    public Map<Long, Invoice.InvoiceStatus> getInvoiceStatusesByOrderIds(Collection<Long> orderIds) {
        return getInvoicesByOrderIds(orderIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatus()));
    }

    public Invoice createInvoice(Invoice invoice) {
        Order order = orderRepository.findById(invoice.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("order", invoice.getOrder().getId()));
        // On ne facture que ses propres ventes : facturer celle d'un collègue reviendrait à
        // engager son chiffre et lui retirerait la main sur son dossier.
        cashierScope.requireAccess(order);

        // La facturation précède la livraison : seule une commande CONFIRMED peut être facturée.
        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw BusinessException.of("invoice.order.mustBeConfirmed",
                    "La commande doit être confirmée pour être facturée (statut actuel : " + order.getStatus() + ")",
                    order.getStatus());
        }

        // Vérifier qu'une facture n'existe pas déjà pour cette commande
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw BusinessException.of("invoice.alreadyExists",
                    "Une facture existe déjà pour cette commande");
        }

        // Calcul du montant : sous-total (HT) → remise → TVA appliquée sur le net après remise → total TTC.
        // La taxe est arrondie à 2 décimales (HALF_UP) pour éviter les écarts de centimes.
        BigDecimal subtotal = order.getTotalAmount();
        invoice.setSubtotal(subtotal);

        // La remise portée par la commande est reprise ici, cumulée à l'éventuelle remise
        // consentie au moment de la facturation : sans cela, la facture réclamait le total brut
        // et ne correspondait plus au montant final de la commande.
        BigDecimal orderDiscount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
        BigDecimal invoiceDiscount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
        BigDecimal discount = orderDiscount.add(invoiceDiscount);

        // Une remise supérieure au sous-total donnerait une facture à total négatif : son reste
        // à payer le serait aussi, et recordPayment refuserait alors tout encaissement.
        if (discount.compareTo(subtotal) > 0) {
            throw BusinessException.of("invoice.discount.exceedsSubtotal",
                    "La remise (" + discount + " €) dépasse le sous-total de la facture ("
                            + subtotal + " €)",
                    discount, subtotal);
        }

        invoice.setDiscount(discount);
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

        // La commande passe de CONFIRMED à INVOICED — pré-requis pour créer la livraison.
        orderService.transitionTo(order, Order.OrderStatus.INVOICED);

        logActivity(ActivityLog.ActionType.CREATE, "Invoice", savedInvoice.getId(),
            "Création de la facture " + savedInvoice.getInvoiceNumber() + " - Montant: " + savedInvoice.getTotalAmount());

        return savedInvoice;
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod) {
        return recordPayment(id, amount, paymentMethod, LocalDate.now());
    }

    public Invoice recordPayment(Long id, BigDecimal amount, Invoice.PaymentMethod paymentMethod, LocalDate paymentDate) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", id));
        cashierScope.requireAccess(invoice);

        // Règles métier d'encaissement : on ne paie ni une facture annulée ni une facture soldée.
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED) {
            throw BusinessException.of("invoice.payment.canceled",
                    "Impossible d'enregistrer un paiement sur une facture annulée");
        }
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.of("invoice.alreadyPaid", "La facture est déjà payée");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.of("payment.amount.positive",
                    "Le montant du paiement doit être positif");
        }

        // Montant normalisé à 2 décimales, cohérent avec la précision monétaire stockée.
        BigDecimal payment = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal alreadyPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = invoice.getTotalAmount().subtract(alreadyPaid);

        // Un paiement (partiel ou total) ne peut pas dépasser le reste à payer. On refuse explicitement
        // plutôt que d'écrêter en silence : l'encaissement enregistré doit refléter le montant réel.
        if (payment.compareTo(remaining) > 0) {
            throw BusinessException.of("payment.amount.exceedsRemaining",
                    "Le montant dépasse le reste à payer (" + remaining + " €)", remaining);
        }

        BigDecimal newPaidAmount = alreadyPaid.add(payment);
        invoice.setPaidAmount(newPaidAmount);
        invoice.setPaymentMethod(paymentMethod);

        // Solde atteint → facture soldée (la date de paiement marque le règlement complet) ;
        // sinon paiement partiel, la facture reste due.
        if (newPaidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaidAmount(invoice.getTotalAmount());
            invoice.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIALLY_PAID);
        }

        Invoice savedInvoice = invoiceRepository.save(invoice);

        logActivity(ActivityLog.ActionType.PAYMENT, "Invoice", savedInvoice.getId(),
            "Paiement de " + payment + " sur la facture " + savedInvoice.getInvoiceNumber() + " (" + paymentMethod + ")");

        return savedInvoice;
    }

    public void cancelInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", id));
        cashierScope.requireAccess(invoice);

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw BusinessException.of("invoice.cancel.alreadyPaid",
                    "Impossible d'annuler une facture déjà payée");
        }

        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);
        invoiceRepository.save(invoice);

        logActivity(ActivityLog.ActionType.UPDATE, "Invoice", id,
            "Annulation de la facture " + invoice.getInvoiceNumber());
    }

    public void deleteInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", id));
        cashierScope.requireAccess(invoice);
        String invoiceNumber = invoice.getInvoiceNumber();
        invoiceRepository.delete(invoice);

        logActivity(ActivityLog.ActionType.DELETE, "Invoice", id,
            "Suppression de la facture " + invoiceNumber);
    }
}
