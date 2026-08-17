package com.gescom.backend.service;

import com.gescom.backend.dto.delivery.DeliverySearchCriteria;
import com.gescom.backend.dto.delivery.DeliverySummary;
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
import com.gescom.backend.security.CashierScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final CashierScope cashierScope;

    public DeliveryService(DeliveryRepository deliveryRepository, OrderRepository orderRepository,
                           InvoiceRepository invoiceRepository, OrderService orderService,
                           ActivityLogService activityLogService, CashierScope cashierScope) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.orderService = orderService;
        this.activityLogService = activityLogService;
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
    public List<Delivery> getAllDeliveries() {
        // Chargement de la commande associée (client, créateur, lignes, produits) en une requête
        // pour éviter le N+1 au mapping (chaque DeliveryResponse embarque un OrderResponse complet).
        return deliveryRepository.findAllWithDetails(cashierScope.restrictedUserId());
    }

    /**
     * Page de livraisons, filtrée et triée en base, chaque ligne entièrement chargée.
     *
     * Même recherche en deux temps que pour les commandes et les factures : la réponse embarque
     * la commande livrée avec ses lignes, donc un JOIN FETCH sur une collection, qu'une base ne
     * sait pas paginer.
     */
    @Transactional(readOnly = true)
    public Page<Delivery> searchDeliveries(DeliverySearchCriteria criteria, Pageable pageable) {
        Page<Delivery> idPage = deliveryRepository.findAll(buildFilter(criteria), pageable);
        List<Long> ids = idPage.getContent().stream().map(Delivery::getId).toList();
        if (ids.isEmpty()) {
            return idPage;
        }
        Map<Long, Delivery> byId = deliveryRepository.findAllWithDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Delivery::getId, d -> d));
        List<Delivery> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    /** Compteurs d'en-tête, agrégés en base — voir {@link DeliverySummary}. */
    @Transactional(readOnly = true)
    public DeliverySummary getSummary() {
        DeliveryRepository.DeliverySummaryView v = deliveryRepository.summaryFor(
                cashierScope.restrictedUserId(), LocalDate.now().atStartOfDay(),
                Delivery.DeliveryStatus.PENDING, Delivery.DeliveryStatus.DELIVERED);
        return new DeliverySummary(v.getTotal(), v.getPending(), v.getDelivered(), v.getLate());
    }

    /** Clients, villes et pays proposés par les filtres — voir {@link DeliverySummary.FilterOptions}. */
    @Transactional(readOnly = true)
    public DeliverySummary.FilterOptions getFilterOptions() {
        Long restrictedUserId = cashierScope.restrictedUserId();
        List<DeliverySummary.Option> clients = deliveryRepository.findDistinctClients(restrictedUserId)
                .stream()
                .map(v -> new DeliverySummary.Option(v.getId(), clientLabel(v)))
                .toList();
        return new DeliverySummary.FilterOptions(
                clients,
                deliveryRepository.findDistinctCities(restrictedUserId),
                deliveryRepository.findDistinctCountries(restrictedUserId));
    }

    /** « Prénom Nom », à défaut la raison sociale — la règle qu'appliquait l'écran. */
    private String clientLabel(DeliveryRepository.DeliveryClientView v) {
        String composed = Stream.of(v.getFirstName(), v.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        if (!composed.isBlank()) return composed;
        return v.getCompany() != null && !v.getCompany().isBlank() ? v.getCompany() : "#" + v.getId();
    }

    private Specification<Delivery> buildFilter(DeliverySearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Cloisonnement caissier, évalué EN BASE : appliqué après coup, il rendrait des
            // pages à moitié vides et un total faux.
            Long restrictedUserId = cashierScope.restrictedUserId();
            if (restrictedUserId != null) {
                predicates.add(cb.equal(root.get("order").get("createdBy").get("id"), restrictedUserId));
            }

            if (c.late()) {
                // « En retard » n'est pas un statut : c'est une date prévue dépassée sur une
                // livraison ENCORE EN ATTENTE. Une livraison effectuée en retard n'est plus en
                // retard, elle est faite.
                predicates.add(cb.and(
                        cb.equal(root.get("status"), Delivery.DeliveryStatus.PENDING),
                        cb.isNotNull(root.get("scheduledDate")),
                        cb.lessThan(root.get("scheduledDate"), LocalDate.now().atStartOfDay())));
            } else if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }

            if (c.clientId() != null) {
                predicates.add(cb.equal(root.get("order").get("client").get("id"), c.clientId()));
            }
            if (c.city() != null && !c.city().isBlank()) {
                predicates.add(cb.equal(root.get("deliveryCity"), c.city()));
            }
            if (c.country() != null && !c.country().isBlank()) {
                predicates.add(cb.equal(root.get("deliveryCountry"), c.country()));
            }
            if (c.contact() != null && !c.contact().isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")),
                        "%" + c.contact().toLowerCase().trim() + "%"));
            }
            if (c.scheduledFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("scheduledDate"), c.scheduledFrom().atStartOfDay()));
            }
            if (c.scheduledTo() != null) {
                // Borne haute exclusive au lendemain minuit : scheduledDate est un horodatage,
                // une livraison prévue à 14 h le jour de fin serait sinon écartée.
                predicates.add(cb.lessThan(
                        root.get("scheduledDate"), c.scheduledTo().plusDays(1).atStartOfDay()));
            }
            if (c.search() != null && !c.search().isBlank()) {
                String pattern = "%" + c.search().toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        like(cb, root.get("deliveryNumber"), pattern),
                        like(cb, root.get("contactName"), pattern),
                        like(cb, root.get("deliveryCity"), pattern),
                        like(cb, root.get("order").get("orderNumber"), pattern),
                        like(cb, root.get("order").get("client").get("firstName"), pattern),
                        like(cb, root.get("order").get("client").get("lastName"), pattern),
                        like(cb, root.get("order").get("client").get("company"), pattern)));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** COALESCE avant LOWER : une colonne nulle rendrait la comparaison indéfinie, pas fausse. */
    private Predicate like(CriteriaBuilder cb, Path<?> path, String pattern) {
        return cb.like(cb.lower(cb.coalesce(path.as(String.class), "")), pattern);
    }

    /**
     * Lecture unitaire. Hors périmètre du caissier, la livraison est rendue absente (404) plutôt
     * que refusée — même règle que pour la vente dont elle découle.
     */
    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryById(Long id) {
        return cashierScope.filterReadable(deliveryRepository.findById(id), Delivery::getOrder);
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryByDeliveryNumber(String deliveryNumber) {
        return cashierScope.filterReadable(
                deliveryRepository.findByDeliveryNumber(deliveryNumber), Delivery::getOrder);
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryByOrder(Long orderId) {
        return cashierScope.filterReadable(deliveryRepository.findByOrderId(orderId), Delivery::getOrder);
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByStatus(Delivery.DeliveryStatus status) {
        return deliveryRepository.findByStatus(status, cashierScope.restrictedUserId());
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByDateRange(LocalDateTime start, LocalDateTime end) {
        return deliveryRepository.findByScheduledDateBetween(start, end, cashierScope.restrictedUserId());
    }

    public Delivery createDelivery(Delivery delivery) {
        Order order = orderRepository.findById(delivery.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("order", delivery.getOrder().getId()));
        // On ne planifie une livraison que sur ses propres ventes.
        cashierScope.requireAccess(order);

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
        cashierScope.requireAccess(existingDelivery);

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
        cashierScope.requireAccess(delivery);

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
        cashierScope.requireAccess(delivery);

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
        cashierScope.requireAccess(delivery);
        deliveryRepository.delete(delivery);

        logActivity(ActivityLog.ActionType.DELETE, "Delivery", id,
                "Suppression de la livraison " + delivery.getDeliveryNumber());
    }
}
