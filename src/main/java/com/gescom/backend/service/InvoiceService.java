package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.dto.invoice.InvoiceSearchCriteria;
import com.gescom.backend.dto.invoice.InvoiceSummary;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.security.CashierScope;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service des factures : création, paiements et annulation.
 * C'est l'étape du milieu dans la vie d'une commande : facturer fait passer la commande de
 * CONFIRMED à INVOICED (obligatoire avant la livraison). Le statut de la facture
 * (UNPAID / PARTIALLY_PAID / PAID) dépend de ce qui a été payé par rapport au total.
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
    // Utilisés seulement par requireDeletable : on ne supprime pas une facture sans savoir ce
    // qui est lié à elle.
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;

    /** Generation des numeros de documents : une suite par entreprise et par annee. */
    private final DocumentNumberService documentNumberService;

    public InvoiceService(InvoiceRepository invoiceRepository, OrderRepository orderRepository,
                          ActivityLogService activityLogService, OrderService orderService,
                          CashierScope cashierScope, PaymentRepository paymentRepository,
                          DeliveryRepository deliveryRepository,
                              DocumentNumberService documentNumberService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.activityLogService = activityLogService;
        this.orderService = orderService;
        this.cashierScope = cashierScope;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.documentNumberService = documentNumberService;
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
        // Toujours le même ordre (facture la plus récente en premier) + la commande chargée
        // dans la même requête pour éviter le N+1 au mapping (chaque réponse contient un
        // OrderResponse complet).
        return invoiceRepository.findAllWithDetails(cashierScope.restrictedUserId());
    }

    /**
     * Une page de factures, filtrée et triée en base, chaque ligne chargée en entier.
     *
     * Même recherche en deux étapes que pour les commandes, pour la même raison : la réponse
     * contient la commande avec ses lignes, donc un JOIN FETCH sur une collection, et une
     * requête comme ça ne peut pas être paginée en base (Hibernate chargerait tout et
     * découperait en mémoire). On sélectionne donc d'abord les factures sans le détail, puis on
     * charge le détail seulement pour celles de la page.
     */
    @Transactional(readOnly = true)
    public Page<Invoice> searchInvoices(InvoiceSearchCriteria criteria, Pageable pageable) {
        Page<Invoice> idPage = invoiceRepository.findAll(buildFilter(criteria), pageable);
        List<Long> ids = idPage.getContent().stream().map(Invoice::getId).toList();
        if (ids.isEmpty()) {
            return idPage;
        }
        Map<Long, Invoice> byId = invoiceRepository.findAllWithDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Invoice::getId, i -> i));
        List<Invoice> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    /** Compteurs en haut de page, calculés en base (voir {@link InvoiceSummary}). */
    @Transactional(readOnly = true)
    public InvoiceSummary getSummary() {
        InvoiceRepository.InvoiceSummaryView v = invoiceRepository.summaryFor(
                cashierScope.restrictedUserId(), LocalDate.now(),
                Invoice.InvoiceStatus.UNPAID, Invoice.InvoiceStatus.PARTIALLY_PAID,
                Invoice.InvoiceStatus.PAID, Invoice.InvoiceStatus.CANCELED);
        return new InvoiceSummary(v.getTotal(), v.getCollected(), v.getPending(), v.getOverdue(),
                v.getUnpaid(), v.getPartial(), v.getPaid(), v.getCanceled());
    }

    /** Clients proposés dans le filtre : seulement ceux qui ont une facture. */
    @Transactional(readOnly = true)
    public InvoiceSummary.FilterOptions getFilterOptions() {
        List<InvoiceSummary.ClientOption> clients =
                invoiceRepository.findDistinctClients(cashierScope.restrictedUserId()).stream()
                        .map(v -> new InvoiceSummary.ClientOption(v.getId(), clientLabel(v)))
                        .toList();
        return new InvoiceSummary.FilterOptions(clients);
    }

    /** « Prénom Nom », sinon la raison sociale (comme faisait l'écran). */
    private String clientLabel(InvoiceRepository.InvoiceClientView v) {
        String composed = Stream.of(v.getFirstName(), v.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        if (!composed.isBlank()) return composed;
        return v.getCompany() != null && !v.getCompany().isBlank() ? v.getCompany() : "#" + v.getId();
    }

    private Specification<Invoice> buildFilter(InvoiceSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Limite du caissier à ses ventes, faite EN BASE : si on filtrait après, on aurait
            // des pages à moitié vides et un total faux.
            Long restrictedUserId = cashierScope.restrictedUserId();
            if (restrictedUserId != null) {
                predicates.add(cb.equal(root.get("order").get("createdBy").get("id"), restrictedUserId));
            }

            if (c.overdue()) {
                // « En retard » n'est pas un statut : c'est une facture ni payée ni annulée dont
                // l'échéance est passée. L'écran l'affiche dans la même liste que les statuts,
                // d'où ce filtre à part.
                predicates.add(cb.and(
                        cb.isNotNull(root.get("dueDate")),
                        cb.lessThan(root.get("dueDate"), LocalDate.now()),
                        cb.notEqual(root.get("status"), Invoice.InvoiceStatus.PAID),
                        cb.notEqual(root.get("status"), Invoice.InvoiceStatus.CANCELED)));
            } else if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }

            if (c.clientId() != null) {
                predicates.add(cb.equal(root.get("order").get("client").get("id"), c.clientId()));
            }
            if (c.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), c.paymentMethod()));
            }
            if (c.issuedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), c.issuedFrom()));
            }
            if (c.issuedTo() != null) {
                // invoiceDate est une DATE (sans heure) : la borne de fin reste incluse telle
                // quelle, contrairement aux écrans qui filtrent sur createdAt.
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), c.issuedTo()));
            }
            if (c.amountMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), c.amountMin()));
            }
            if (c.amountMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), c.amountMax()));
            }
            if (c.onlyRemaining()) {
                // Même marge au centime que l'écran : un reste de 0,0004 € vient d'un arrondi,
                // pas d'une vraie dette.
                predicates.add(cb.greaterThan(
                        cb.diff(root.get("totalAmount"), cb.coalesce(root.get("paidAmount"), BigDecimal.ZERO)),
                        new BigDecimal("0.001")));
            }
            if (c.search() != null && !c.search().isBlank()) {
                String pattern = "%" + c.search().toLowerCase().trim() + "%";
                // LEFT JOIN sur le client : Order.client peut être null (vente sans client).
                // Avec root.get(...) on aurait un INNER JOIN, et la facture d'une vente sans
                // client ne serait plus trouvée par la recherche.
                Join<Object, Object> order = root.join("order", JoinType.LEFT);
                Join<Object, Object> client = order.join("client", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("invoiceNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(order.get("orderNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(client.get("firstName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(client.get("lastName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(client.get("company"), "")), pattern)));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Lecture d'une facture. Si elle n'est pas au caissier, on fait comme si elle n'existait
     * pas (404) au lieu de refuser, comme pour la vente.
     */
    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceById(Long id) {
        Optional<Invoice> invoiceOpt = cashierScope.filterReadable(
                invoiceRepository.findById(id), Invoice::getOrder);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            // Force le chargement des relations lazy
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

    // ── Calculs du tableau de bord caisse (filtrés sur Order.createdBy) ───────────

    @Transactional(readOnly = true)
    public BigDecimal getCollectedByCashierOnDate(Long userId, LocalDate date) {
        return invoiceRepository.sumCollectedByCashierOnDate(userId, date);
    }

    /**
     * Encaissé du jour par caissier. Les caissiers sans encaissement ne sont pas dans la map,
     * l'appelant met zéro.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCollectedPerCashierOnDate(LocalDate date) {
        return invoiceRepository.sumCollectedPerCashierOnDate(date).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));
    }

    /**
     * La facture (s'il y en a une) de chaque commande donnée, en une seule requête.
     *
     * Elle donne deux infos que la commande seule n'a pas : son statut (d'où le « Payée »
     * d'une commande facturée et payée, dont le statut reste INVOICED jusqu'à la livraison)
     * et son total TTC, le vrai montant demandé au client.
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

    /** Seulement les statuts, pour ceux qui n'ont pas besoin des montants. */
    @Transactional(readOnly = true)
    public Map<Long, Invoice.InvoiceStatus> getInvoiceStatusesByOrderIds(Collection<Long> orderIds) {
        return getInvoicesByOrderIds(orderIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatus()));
    }

    public Invoice createInvoice(Invoice invoice) {
        Order order = orderRepository.findById(invoice.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("order", invoice.getOrder().getId()));
        // On ne facture que ses propres ventes : facturer celle d'un collègue changerait son
        // chiffre et il ne pourrait plus gérer son dossier.
        cashierScope.requireAccess(order);

        // On facture avant de livrer : seule une commande CONFIRMED peut être facturée.
        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw BusinessException.of("invoice.order.mustBeConfirmed",
                    "La commande doit être confirmée pour être facturée (statut actuel : " + order.getStatus() + ")",
                    order.getStatus());
        }

        // Vérifie qu'il n'y a pas déjà une facture pour cette commande
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw BusinessException.of("invoice.alreadyExists",
                    "Une facture existe déjà pour cette commande");
        }

        // Calcul : sous-total (HT) → remise → TVA sur le montant après remise → total TTC.
        // La TVA est arrondie à 2 décimales (HALF_UP) pour éviter les écarts de centimes.
        BigDecimal subtotal = order.getTotalAmount();
        invoice.setSubtotal(subtotal);

        // On reprend la remise de la commande, en plus de la remise éventuelle donnée au
        // moment de facturer : sans ça, la facture demandait le total brut et ne
        // correspondait plus au montant final de la commande.
        BigDecimal orderDiscount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;
        BigDecimal invoiceDiscount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
        BigDecimal discount = orderDiscount.add(invoiceDiscount);

        // Une remise plus grande que le sous-total donnerait une facture négative : le reste à
        // payer serait négatif aussi, et recordPayment refuserait alors tout paiement.
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

        // Numéro donné dans cette transaction : le verrou reste jusqu'au commit, donc après
        // l'enregistrement de la facture.
        invoice.setInvoiceNumber(documentNumberService.next(DocumentNumberService.DocumentType.INVOICE));

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // La commande passe de CONFIRMED à INVOICED, ce qui permet ensuite de créer la livraison.
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

        // On ne peut pas payer une facture annulée ni une facture déjà payée.
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

        // Montant arrondi à 2 décimales, comme les montants enregistrés en base.
        BigDecimal payment = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal alreadyPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = invoice.getTotalAmount().subtract(alreadyPaid);

        // Un paiement (partiel ou total) ne peut pas dépasser le reste à payer. On refuse
        // clairement plutôt que de réduire le montant sans rien dire : le paiement enregistré
        // doit être le vrai montant.
        if (payment.compareTo(remaining) > 0) {
            throw BusinessException.of("payment.amount.exceedsRemaining",
                    "Le montant dépasse le reste à payer (" + remaining + " €)", remaining);
        }

        BigDecimal newPaidAmount = alreadyPaid.add(payment);
        invoice.setPaidAmount(newPaidAmount);
        invoice.setPaymentMethod(paymentMethod);

        // Tout est payé → facture PAID (la date de paiement indique que c'est réglé) ;
        // sinon paiement partiel, il reste quelque chose à payer.
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
        requireDeletable(invoice);

        String invoiceNumber = invoice.getInvoiceNumber();
        invoiceRepository.delete(invoice);

        logActivity(ActivityLog.ActionType.DELETE, "Invoice", id,
            "Suppression de la facture " + invoiceNumber);
    }

    /**
     * Conditions pour pouvoir supprimer une facture.
     *
     * Avant, la suppression effaçait la ligne sans rien vérifier, alors que le reste est
     * protégé : {@code OrderService.requireNoReturns} refuse d'annuler une commande qui a un
     * retour, et {@code cancelOrder} refuse une commande dont la facture est encore active.
     * La facture, elle, était supprimée peu importe ce qui y était lié.
     *
     * On applique la règle de la comptabilité, la même que pour la commande :
     * <b>un document émis ne se supprime pas, il s'annule</b>. {@link #cancelInvoice} est la
     * façon normale de faire ; la suppression sert juste à faire le ménage sur un document qui
     * ne compte déjà plus. Elle n'est d'ailleurs disponible que par l'API, aucun écran ne la
     * propose.
     *
     * Quatre refus, du plus grave au plus formel :
     *
     * <ol>
     *   <li><b>Il y a eu un paiement.</b> Supprimer ferait disparaître la trace d'argent
     *       vraiment reçu. Ça peut arriver même sur une facture annulée : {@code cancelInvoice}
     *       ne refuse que les factures payées en entier, pas celles payées en partie.</li>
     *   <li><b>Il y a un paiement carte lié.</b> La clé étrangère de {@code payments} ferait
     *       échouer la suppression de toute façon, mais avec une 409 et un message technique.
     *       Ça peut arriver sans paiement réel : une intention de paiement en attente ou
     *       refusée crée une ligne sans jamais payer la facture.</li>
     *   <li><b>Il y a une livraison sur la commande.</b> Elle n'a pu être créée que parce que
     *       la commande était facturée ; sans facture elle n'aurait plus de raison d'exister.
     *       Ça peut arriver, car {@code cancelInvoice} ne vérifie pas les livraisons.</li>
     *   <li><b>La facture n'est pas annulée.</b> Dernière vérification, la plus formelle : elle
     *       oblige à passer par l'annulation, qui est enregistrée dans le journal d'activité et
     *       refuse les factures payées.</li>
     * </ol>
     *
     * Ce que ça ne règle pas : la commande reste en {@code INVOICED}, et depuis ce statut on ne
     * peut plus ni la refacturer ({@code createInvoice} demande {@code CONFIRMED}) ni la
     * livrer. Pour la remettre en {@code CONFIRMED}, il faudrait autoriser un retour en arrière
     * dans {@code ALLOWED_TRANSITIONS}, donc aussi via {@code PATCH /orders/{id}/status}, ce
     * qui serait pire. Pour corriger une facture fausse, on annule la vente ; une note de
     * crédit serait la bonne évolution pour gérer ce cas proprement.
     */
    private void requireDeletable(Invoice invoice) {
        BigDecimal paid = invoice.getPaidAmount();
        if (paid != null && paid.compareTo(BigDecimal.ZERO) > 0) {
            throw BusinessException.of("invoice.delete.paymentRecorded",
                    "La facture " + invoice.getInvoiceNumber() + " porte un encaissement de "
                            + paid + " € : la supprimer effacerait la trace de cet argent reçu.",
                    invoice.getInvoiceNumber(), paid);
        }

        if (!paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).isEmpty()) {
            throw BusinessException.of("invoice.delete.cardPaymentAttached",
                    "Des paiements par carte sont rattachés à la facture "
                            + invoice.getInvoiceNumber() + " : la supprimer romprait leur suivi.",
                    invoice.getInvoiceNumber());
        }

        Order order = invoice.getOrder();
        if (order != null && deliveryRepository.findByOrderId(order.getId()).isPresent()) {
            throw BusinessException.of("invoice.delete.deliveryAttached",
                    "Une livraison est rattachée à la commande " + order.getOrderNumber()
                            + " : elle n'existe que parce que cette commande a été facturée.",
                    order.getOrderNumber());
        }

        if (invoice.getStatus() != Invoice.InvoiceStatus.CANCELED) {
            throw BusinessException.of("invoice.delete.notCanceled",
                    "La facture " + invoice.getInvoiceNumber() + " doit d'abord être annulée : "
                            + "un document émis s'annule, il ne se détruit pas.",
                    invoice.getInvoiceNumber());
        }
    }
}
