package com.gescom.backend.service;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    public Optional<Delivery> getDeliveryById(Long id) {
        return deliveryRepository.findById(id);
    }

    public Optional<Delivery> getDeliveryByDeliveryNumber(String deliveryNumber) {
        return deliveryRepository.findByDeliveryNumber(deliveryNumber);
    }

    public Optional<Delivery> getDeliveryByOrder(Long orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }

    public List<Delivery> getDeliveriesByStatus(Delivery.DeliveryStatus status) {
        return deliveryRepository.findByStatus(status);
    }

    public List<Delivery> getDeliveriesByDateRange(LocalDateTime start, LocalDateTime end) {
        return deliveryRepository.findByScheduledDateBetween(start, end);
    }

    public Delivery createDelivery(Delivery delivery) {
        // Check if order exists and is confirmed
        Order order = orderRepository.findById(delivery.getOrder().getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new RuntimeException("Order must be confirmed before creating delivery");
        }

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // Update order status
        order.setStatus(Order.OrderStatus.DELIVERED);
        orderRepository.save(order);

        return savedDelivery;
    }

    public Delivery updateDelivery(Long id, Delivery updatedDelivery) {
        Delivery existingDelivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        // Update editable fields
        existingDelivery.setDeliveryAddress(updatedDelivery.getDeliveryAddress());
        existingDelivery.setDeliveryCity(updatedDelivery.getDeliveryCity());
        existingDelivery.setDeliveryPostalCode(updatedDelivery.getDeliveryPostalCode());
        existingDelivery.setDeliveryCountry(updatedDelivery.getDeliveryCountry());
        existingDelivery.setContactName(updatedDelivery.getContactName());
        existingDelivery.setContactPhone(updatedDelivery.getContactPhone());
        existingDelivery.setScheduledDate(updatedDelivery.getScheduledDate());
        existingDelivery.setStatus(updatedDelivery.getStatus());
        existingDelivery.setNotes(updatedDelivery.getNotes());

        return deliveryRepository.save(existingDelivery);
    }

    public Delivery updateDeliveryStatus(Long id, Delivery.DeliveryStatus status) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        delivery.setStatus(status);

        if (status == Delivery.DeliveryStatus.DELIVERED) {
            delivery.setDeliveredDate(LocalDateTime.now());
        }

        return deliveryRepository.save(delivery);
    }

    public Delivery markAsDelivered(Long id, String deliveredBy) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        delivery.setStatus(Delivery.DeliveryStatus.DELIVERED);
        delivery.setDeliveredDate(LocalDateTime.now());
        delivery.setDeliveredBy(deliveredBy);

        return deliveryRepository.save(delivery);
    }

    public Invoice createInvoiceFromDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        // Vérifier que la livraison est livrée
        if (delivery.getStatus() != Delivery.DeliveryStatus.DELIVERED) {
            throw new RuntimeException("Delivery must be delivered before creating invoice");
        }

        // Vérifier qu'il n'existe pas déjà une facture pour cette livraison
        Optional<Invoice> existingInvoice = invoiceRepository.findByDelivery(delivery);
        if (existingInvoice.isPresent()) {
            throw new RuntimeException("Invoice already exists for this delivery");
        }

        Order order = delivery.getOrder();

        // Calculer le total de la commande
        BigDecimal subtotal = order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Créer la facture
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setDelivery(delivery);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30)); // Échéance à 30 jours
        invoice.setSubtotal(subtotal);
        invoice.setTaxRate(new BigDecimal("20.00")); // TVA 20%
        invoice.setTaxAmount(subtotal.multiply(new BigDecimal("0.20")));
        invoice.setTotalAmount(subtotal.add(subtotal.multiply(new BigDecimal("0.20"))));
        invoice.setRemainingAmount(invoice.getTotalAmount());
        invoice.setStatus(Invoice.InvoiceStatus.UNPAID);
        invoice.setPaymentMethod(Invoice.PaymentMethod.CASH);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Mettre à jour le statut de la livraison
        delivery.setStatus(Delivery.DeliveryStatus.INVOICED);
        deliveryRepository.save(delivery);

        return savedInvoice;
    }

    public void deleteDelivery(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        deliveryRepository.delete(delivery);
    }
}
