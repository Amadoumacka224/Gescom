package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service métier des livraisons : création, suivi de statut et confirmation de livraison.
 * Dernière étape du cycle de vie d'une commande — une livraison ne peut exister que pour
 * une commande facturée (INVOICED), et son passage à DELIVERED fait basculer l'Order en
 * DELIVERED (état commercial final). Règle : une seule livraison par commande.
 */
@Service
@Transactional
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrderService orderService;
    private final ActivityLogService activityLogService;

    public DeliveryService(DeliveryRepository deliveryRepository, OrderRepository orderRepository,
                           InvoiceRepository invoiceRepository, OrderService orderService,
                           ActivityLogService activityLogService) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderService = orderService;
        this.activityLogService = activityLogService;
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
    public List<Delivery> getAllDeliveries() {
        // Chargement de la commande associée (client, créateur, lignes, produits) en une requête
        // pour éviter le N+1 au mapping (chaque DeliveryResponse embarque un OrderResponse complet).
        return deliveryRepository.findAllWithDetails();
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
                .orElseThrow(() -> new ResourceNotFoundException("order", delivery.getOrder().getId()));

        // Pré-requis métier : la livraison ne peut être créée qu'après la facturation.
        if (order.getStatus() != Order.OrderStatus.INVOICED) {
            throw new BusinessException(
                    "La commande doit être facturée pour être livrée (statut actuel : " + order.getStatus() + ")");
        }

        // Défense en profondeur : statut INVOICED implique en principe la présence d'une facture
        // valide, mais on vérifie explicitement pour rejeter le cas d'une facture annulée.
        Invoice invoice = invoiceRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(
                        "Aucune facture n'a été émise pour cette commande — impossible de créer la livraison"));
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELED) {
            throw new BusinessException(
                    "La facture associée à cette commande est annulée — impossible de créer la livraison");
        }

        // Une seule livraison par commande.
        if (deliveryRepository.findByOrderId(order.getId()).isPresent()) {
            throw BusinessException.of("delivery.alreadyExists",
                    "Une livraison existe déjà pour cette commande");
        }

        // Les nouvelles livraisons démarrent toujours en PENDING — le statut envoyé par
        // le client est ignoré pour éviter les sauts de cycle.
        delivery.setStatus(Delivery.DeliveryStatus.PENDING);
        delivery.setDeliveredDate(null);
        delivery.setDeliveredBy(null);

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // L'Order reste en INVOICED tant que la livraison n'est pas effectivement
        // marquée DELIVERED — la transition Order INVOICED → DELIVERED est faite par
        // markDeliveryAsDelivered() pour refléter l'état réel.

        logActivity(ActivityLog.ActionType.CREATE, "Delivery", savedDelivery.getId(),
                "Création de la livraison " + savedDelivery.getDeliveryNumber()
                        + " pour la commande " + order.getOrderNumber());

        return savedDelivery;
    }

    /**
     * Bascule la livraison à DELIVERED et propage la transition à l'Order
     * (INVOICED → DELIVERED). Idempotent si déjà DELIVERED.
     */
    private void applyDelivered(Delivery delivery, String deliveredBy) {
        if (delivery.getStatus() == Delivery.DeliveryStatus.DELIVERED) {
            return;
        }
        delivery.setStatus(Delivery.DeliveryStatus.DELIVERED);
        if (delivery.getDeliveredDate() == null) {
            delivery.setDeliveredDate(LocalDateTime.now());
        }
        if (deliveredBy != null && !deliveredBy.isBlank()) {
            delivery.setDeliveredBy(deliveredBy);
        }
        Order order = delivery.getOrder();
        if (order != null && order.getStatus() == Order.OrderStatus.INVOICED) {
            orderService.transitionTo(order, Order.OrderStatus.DELIVERED);
        }
    }

    public Delivery updateDelivery(Long id, Delivery updatedDelivery) {
        Delivery existingDelivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));

        Delivery.DeliveryStatus current = existingDelivery.getStatus();
        Delivery.DeliveryStatus target = updatedDelivery.getStatus();
        if (target != null && target != current) {
            if (!current.canTransitionTo(target)) {
                throw new BusinessException(
                        "Transition de statut invalide : " + current + " → " + target);
            }
            if (target == Delivery.DeliveryStatus.DELIVERED) {
                applyDelivered(existingDelivery, existingDelivery.getDeliveredBy());
            } else {
                existingDelivery.setStatus(target);
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
            throw BusinessException.of("status.target.required", "Le statut cible est obligatoire");
        }

        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));

        Delivery.DeliveryStatus current = delivery.getStatus();
        if (current == status) {
            return delivery;
        }
        if (!current.canTransitionTo(status)) {
            throw new BusinessException(
                    "Transition de statut invalide : " + current + " → " + status);
        }

        if (status == Delivery.DeliveryStatus.DELIVERED) {
            applyDelivered(delivery, delivery.getDeliveredBy());
        } else {
            delivery.setStatus(status);
        }

        return deliveryRepository.save(delivery);
    }

    public Delivery markAsDelivered(Long id, String deliveredBy) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));

        Delivery.DeliveryStatus current = delivery.getStatus();
        if (current != Delivery.DeliveryStatus.DELIVERED
                && !current.canTransitionTo(Delivery.DeliveryStatus.DELIVERED)) {
            throw new BusinessException(
                    "Impossible de marquer comme livrée depuis le statut " + current);
        }

        applyDelivered(delivery, deliveredBy);
        Delivery saved = deliveryRepository.save(delivery);

        logActivity(ActivityLog.ActionType.UPDATE, "Delivery", saved.getId(),
                "Livraison " + saved.getDeliveryNumber() + " marquée comme livrée");

        return saved;
    }

    public void deleteDelivery(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));
        deliveryRepository.delete(delivery);

        logActivity(ActivityLog.ActionType.DELETE, "Delivery", id,
                "Suppression de la livraison " + delivery.getDeliveryNumber());
    }
}
