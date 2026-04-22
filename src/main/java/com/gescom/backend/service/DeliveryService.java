package com.gescom.backend.service;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrderService orderService;

    public DeliveryService(DeliveryRepository deliveryRepository, OrderRepository orderRepository,
                           InvoiceRepository invoiceRepository, OrderService orderService) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryById(Long id) {
        return deliveryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryByDeliveryNumber(String deliveryNumber) {
        return deliveryRepository.findByDeliveryNumber(deliveryNumber);
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryByOrder(Long orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByStatus(Delivery.DeliveryStatus status) {
        return deliveryRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByDateRange(LocalDateTime start, LocalDateTime end) {
        return deliveryRepository.findByScheduledDateBetween(start, end);
    }

    public Delivery createDelivery(Delivery delivery) {
        Order order = orderRepository.findById(delivery.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande", delivery.getOrder().getId()));

        // La livraison est possible depuis CONFIRMED (livraison avant facturation)
        // ou depuis INVOICED (livraison après facturation)
        if (order.getStatus() != Order.OrderStatus.CONFIRMED && order.getStatus() != Order.OrderStatus.INVOICED) {
            throw new BusinessException("La commande doit être confirmée ou facturée pour être livrée (statut actuel : " + order.getStatus() + ")");
        }

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // Transition du statut de la commande (machine à états centralisée dans OrderService) :
        // CONFIRMED → DELIVERED (en attente de facturation)
        // INVOICED → COMPLETED (déjà facturée + maintenant livrée = terminée)
        Order.OrderStatus target = order.getStatus() == Order.OrderStatus.INVOICED
                ? Order.OrderStatus.COMPLETED
                : Order.OrderStatus.DELIVERED;
        orderService.transitionTo(order, target);

        return savedDelivery;
    }

    public Delivery updateDelivery(Long id, Delivery updatedDelivery) {
        Delivery existingDelivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", id));

        Delivery.DeliveryStatus current = existingDelivery.getStatus();
        Delivery.DeliveryStatus target = updatedDelivery.getStatus();
        if (target != null && target != current) {
            if (target == Delivery.DeliveryStatus.INVOICED) {
                throw new BusinessException("Le passage à INVOICED doit se faire via la création de facture");
            }
            if (!current.canTransitionTo(target)) {
                throw new BusinessException(
                        "Transition de statut invalide : " + current + " → " + target);
            }
            existingDelivery.setStatus(target);
            if (target == Delivery.DeliveryStatus.DELIVERED && existingDelivery.getDeliveredDate() == null) {
                existingDelivery.setDeliveredDate(LocalDateTime.now());
            }
        }

        existingDelivery.setDeliveryAddress(updatedDelivery.getDeliveryAddress());
        existingDelivery.setDeliveryCity(updatedDelivery.getDeliveryCity());
        existingDelivery.setDeliveryPostalCode(updatedDelivery.getDeliveryPostalCode());
        existingDelivery.setDeliveryCountry(updatedDelivery.getDeliveryCountry());
        existingDelivery.setContactName(updatedDelivery.getContactName());
        existingDelivery.setContactPhone(updatedDelivery.getContactPhone());
        existingDelivery.setScheduledDate(updatedDelivery.getScheduledDate());
        existingDelivery.setNotes(updatedDelivery.getNotes());

        return deliveryRepository.save(existingDelivery);
    }

    public Delivery updateDeliveryStatus(Long id, Delivery.DeliveryStatus status) {
        if (status == null) {
            throw new BusinessException("Le statut cible est obligatoire");
        }
        if (status == Delivery.DeliveryStatus.INVOICED) {
            throw new BusinessException("Le passage à INVOICED doit se faire via la création de facture");
        }

        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", id));

        Delivery.DeliveryStatus current = delivery.getStatus();
        if (current == status) {
            return delivery;
        }
        if (!current.canTransitionTo(status)) {
            throw new BusinessException(
                    "Transition de statut invalide : " + current + " → " + status);
        }

        delivery.setStatus(status);
        if (status == Delivery.DeliveryStatus.DELIVERED && delivery.getDeliveredDate() == null) {
            delivery.setDeliveredDate(LocalDateTime.now());
        }

        return deliveryRepository.save(delivery);
    }

    public Delivery markAsDelivered(Long id, String deliveredBy) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", id));

        Delivery.DeliveryStatus current = delivery.getStatus();
        if (current != Delivery.DeliveryStatus.DELIVERED
                && !current.canTransitionTo(Delivery.DeliveryStatus.DELIVERED)) {
            throw new BusinessException(
                    "Impossible de marquer comme livrée depuis le statut " + current);
        }

        delivery.setStatus(Delivery.DeliveryStatus.DELIVERED);
        delivery.setDeliveredDate(LocalDateTime.now());
        delivery.setDeliveredBy(deliveredBy);

        return deliveryRepository.save(delivery);
    }

    public Invoice createInvoiceFromDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", deliveryId));

        if (!delivery.getStatus().canTransitionTo(Delivery.DeliveryStatus.INVOICED)) {
            throw new BusinessException(
                    "Impossible de facturer une livraison au statut " + delivery.getStatus()
                            + " (elle doit être au statut DELIVERED)");
        }

        Optional<Invoice> existingInvoice = invoiceRepository.findByDelivery(delivery);
        if (existingInvoice.isPresent()) {
            throw new BusinessException("Une facture existe déjà pour cette livraison");
        }

        Order order = delivery.getOrder();

        // Vérifier qu'une facture n'existe pas déjà pour cette commande
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BusinessException("Une facture existe déjà pour cette commande");
        }

        BigDecimal subtotal = order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setDelivery(delivery);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setSubtotal(subtotal);
        invoice.setTaxRate(new BigDecimal("20.00"));
        invoice.setTaxAmount(subtotal.multiply(new BigDecimal("0.20")));
        invoice.setTotalAmount(subtotal.add(subtotal.multiply(new BigDecimal("0.20"))));
        invoice.setRemainingAmount(invoice.getTotalAmount());
        invoice.setStatus(Invoice.InvoiceStatus.UNPAID);
        invoice.setPaymentMethod(Invoice.PaymentMethod.CASH);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        delivery.setStatus(Delivery.DeliveryStatus.INVOICED);
        deliveryRepository.save(delivery);

        // La commande est maintenant livrée ET facturée → COMPLETED (via machine à états)
        orderService.transitionTo(order, Order.OrderStatus.COMPLETED);

        return savedInvoice;
    }

    public void deleteDelivery(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", id));
        deliveryRepository.delete(delivery);
    }
}
