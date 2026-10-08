package com.gescom.backend.service;

import com.gescom.backend.dto.stock.ReturnLookupResponse;
import com.gescom.backend.dto.stock.ReturnableItemResponse;
import com.gescom.backend.dto.stock.StockReturnItemRequest;
import com.gescom.backend.dto.stock.StockReturnRequest;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.StockReturn;
import com.gescom.backend.entity.StockReturnItem;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.InsufficientStockException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.ReferenceMapper;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gestion des retours clients, à partir de la vente d'origine.
 *
 * On part toujours d'une vente : {@link #lookup(String)} retrouve la commande à partir d'un
 * numéro de commande OU de facture, et renvoie ses lignes avec la quantité qu'on peut encore
 * retourner. {@link #createReturn(StockReturnRequest)} refait ensuite ce contrôle côté
 * serveur (l'écran limite la saisie, le service s'assure que c'est respecté), puis applique
 * les mouvements de stock et enregistre le retour.
 *
 * Les quantités sont modifiées avec un verrou pessimiste ({@code findByIdForUpdate}), comme
 * dans {@link OrderService} : un retour et une confirmation de commande peuvent toucher le
 * même produit au même moment. Pour la même raison, le mouvement est écrit par une méthode
 * locale et pas par {@link StockService}, qui rechargerait le produit sans verrou.
 *
 * On verrouille aussi la commande : ce qu'on peut retourner dépend du total des retours déjà
 * faits sur la vente, et le verrou sur les produits ne protège pas ça.
 */
@Service
@Transactional
public class StockReturnService {

    private static final Logger log = LoggerFactory.getLogger(StockReturnService.class);

    /**
     * Statuts où la marchandise est sortie du stock (ça se fait à la confirmation) : ce sont
     * les seuls où on peut faire un retour. Une commande en attente n'a rien sorti, une
     * commande annulée a déjà tout remis.
     */
    private static final Set<Order.OrderStatus> RETURNABLE_STATUSES = EnumSet.of(
            Order.OrderStatus.CONFIRMED, Order.OrderStatus.INVOICED, Order.OrderStatus.DELIVERED);

    private final StockReturnRepository stockReturnRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    /** Generation des numeros de documents : une suite par entreprise et par annee. */
    private final DocumentNumberService documentNumberService;

    public StockReturnService(StockReturnRepository stockReturnRepository,
                              OrderRepository orderRepository,
                              InvoiceRepository invoiceRepository,
                              ProductRepository productRepository,
                              StockMovementRepository stockMovementRepository,
                              UserRepository userRepository,
                              ActivityLogService activityLogService,
                              DocumentNumberService documentNumberService) {
        this.stockReturnRepository = stockReturnRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.documentNumberService = documentNumberService;
    }

    /* ---------- Recherche de la vente ---------- */

    /**
     * Retrouve la vente à partir d'un numéro de commande ou de facture et prépare les lignes
     * qu'on peut retourner. On ignore la casse et on cherche dans les deux : au comptoir, le
     * client peut donner son bon de commande ou sa facture.
     */
    @Transactional(readOnly = true)
    public ReturnLookupResponse lookup(String reference) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty()) {
            throw BusinessException.of("return.reference.required",
                    "Saisissez un numéro de commande ou de facture");
        }

        Order order = orderRepository.findByOrderNumberWithDetails(ref)
                .or(() -> invoiceRepository.findByInvoiceNumberWithDetails(ref).map(Invoice::getOrder))
                .orElseThrow(() -> new ResourceNotFoundException("sale", "reference", ref));

        requireReturnable(order);

        // On relit toujours la facture depuis la commande : peu importe le numéro utilisé, la
        // réponse décrit la même vente de la même façon.
        Invoice invoice = invoiceRepository.findByOrderId(order.getId()).orElse(null);

        return new ReturnLookupResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getFinalAmount(),
                order.getClient() != null ? order.getClient().getId() : null,
                ReferenceMapper.clientName(order.getClient()),
                order.getCreatedBy() != null ? order.getCreatedBy().getUsername() : null,
                invoice != null ? invoice.getId() : null,
                invoice != null ? invoice.getInvoiceNumber() : null,
                invoice != null ? invoice.getStatus() : null,
                invoice != null ? invoice.getInvoiceDate() : null,
                stockReturnRepository.countByOrderId(order.getId()),
                returnableItems(order, paidRatio(order, invoice)));
    }

    /** Refuse tout de suite une vente dont la marchandise n'est pas (ou plus) chez le client. */
    private void requireReturnable(Order order) {
        if (order.getStatus() == Order.OrderStatus.CANCELED) {
            throw BusinessException.of("return.order.canceled",
                    "La commande " + order.getOrderNumber()
                            + " est annulée : son stock a déjà été restitué.",
                    order.getOrderNumber());
        }
        if (!RETURNABLE_STATUSES.contains(order.getStatus())) {
            throw BusinessException.of("return.order.notConfirmed",
                    "La commande " + order.getOrderNumber()
                            + " n'est pas encore confirmée : sa marchandise n'est jamais sortie du stock.",
                    order.getOrderNumber());
        }
    }

    /**
     * Lignes de la vente, avec pour chacune ce qu'on peut encore retourner.
     * Les quantités sont regroupées par produit : une commande peut avoir deux fois le même
     * article, mais on doit afficher un seul total retournable.
     */
    private List<ReturnableItemResponse> returnableItems(Order order, BigDecimal paidRatio) {
        Map<Long, Integer> alreadyReturned = returnedQuantitiesByProduct(order.getId());

        Map<Long, Integer> soldQuantities = new LinkedHashMap<>();
        Map<Long, BigDecimal> soldTotals = new LinkedHashMap<>();
        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            productsById.putIfAbsent(product.getId(), product);
            soldQuantities.merge(product.getId(), item.getQuantity(), Integer::sum);
            soldTotals.merge(product.getId(), item.getTotalPrice(), BigDecimal::add);
        }

        List<ReturnableItemResponse> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : soldQuantities.entrySet()) {
            Long productId = entry.getKey();
            Product product = productsById.get(productId);
            int sold = entry.getValue();
            int returned = alreadyReturned.getOrDefault(productId, 0);
            BigDecimal lineTotal = paidLineTotal(soldTotals.get(productId), paidRatio);

            items.add(new ReturnableItemResponse(
                    productId,
                    product.getCode(),
                    product.getName(),
                    product.getUnit(),
                    sold,
                    returned,
                    Math.max(0, sold - returned),
                    netUnitPrice(lineTotal, sold),
                    lineTotal,
                    product.getStockQuantity()));
        }
        return items;
    }

    /**
     * Part du montant de la ligne que le client a vraiment payée, après la remise globale de
     * la vente.
     *
     * Il y a deux remises : celle de la ligne est déjà enlevée de {@code OrderItem.totalPrice},
     * mais celle de toute la vente est un montant global, sur la commande, puis reprise par la
     * facture (en plus de l'éventuelle remise faite à la facturation). Si on remboursait le
     * total brut de la ligne, on rendrait plus que ce qui a été payé. On répartit donc la
     * remise entre les lignes au prorata, sur leur somme, c'est-à-dire sur le même montant que
     * celui qui a servi à la calculer.
     *
     * Si une facture existe, on l'utilise en priorité : sa remise contient déjà celle de la
     * commande.
     */
    private BigDecimal paidRatio(Order order, Invoice invoice) {
        BigDecimal gross = order.getTotalAmount();
        if (gross == null || gross.signum() <= 0) return BigDecimal.ONE;

        BigDecimal discount = invoice != null && invoice.getDiscount() != null
                ? invoice.getDiscount()
                : order.getDiscount();
        if (discount == null || discount.signum() <= 0) return BigDecimal.ONE;

        BigDecimal net = gross.subtract(discount);
        if (net.signum() <= 0) return BigDecimal.ZERO;

        // Ratio à 6 décimales : c'est une valeur intermédiaire, les montants calculés avec sont
        // arrondis au centime une seule fois, à la fin.
        return net.divide(gross, 6, RoundingMode.HALF_UP);
    }

    /** Total de la ligne vraiment payé : total après remise de ligne, au prorata de la remise globale. */
    private BigDecimal paidLineTotal(BigDecimal lineTotal, BigDecimal paidRatio) {
        if (lineTotal == null) return BigDecimal.ZERO;
        return lineTotal.multiply(paidRatio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Prix unitaire vraiment payé : le total payé de la ligne (toutes remises enlevées) divisé
     * par la quantité. C'est sur ce montant, et pas sur le prix actuel du produit, qu'on
     * rembourse.
     *
     * Juste pour l'affichage : il est arrondi au centime, donc approximatif quand le total ne
     * se divise pas exactement. Le montant remboursé, lui, vient de
     * {@link #refundShare(BigDecimal, int, int)}.
     */
    private BigDecimal netUnitPrice(BigDecimal lineTotal, int quantity) {
        if (quantity <= 0 || lineTotal == null) return BigDecimal.ZERO;
        return lineTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    /**
     * Part du total payé pour les unités rendues : {@code total × rendues / vendues}, arrondi
     * une seule fois à la fin.
     *
     * Si on multipliait le prix unitaire déjà arrondi par la quantité, on perdrait un centime
     * quand le total ne se divise pas exactement : une ligne de 10,00 € pour 3 unités donne
     * 3,33 € l'unité, et rendre les trois rembourserait 9,99 €, le client ne récupérerait pas
     * tout ce qu'il a payé. En partant du total, un retour complet rend exactement le total, et
     * deux retours partiels qui vident la ligne retombent juste (6,67 + 3,33).
     */
    private BigDecimal refundShare(BigDecimal paidLineTotal, int soldQuantity, int returnedQuantity) {
        if (paidLineTotal == null || soldQuantity <= 0) return BigDecimal.ZERO;
        return paidLineTotal.multiply(BigDecimal.valueOf(returnedQuantity))
                .divide(BigDecimal.valueOf(soldQuantity), 2, RoundingMode.HALF_UP);
    }

    private Map<Long, Integer> returnedQuantitiesByProduct(Long orderId) {
        Map<Long, Integer> returned = new LinkedHashMap<>();
        for (Object[] row : stockReturnRepository.sumReturnedQuantitiesByOrder(orderId)) {
            returned.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return returned;
    }

    /* ---------- Enregistrement d'un retour ---------- */

    /**
     * Enregistre un retour : vérifie les quantités, fait les mouvements de stock selon chaque
     * traitement, puis enregistre le document. Tout est dans une seule transaction : un retour
     * appliqué à moitié laisserait un stock faux.
     */
    public StockReturn createReturn(StockReturnRequest request) {
        // On verrouille la commande dès le début : le contrôle des quantités dépend du total
        // des retours déjà faits sur cette vente, que le verrou sur les produits ne protège
        // pas. Sans ça, deux retours en même temps liraient tous les deux « rien de rendu »
        // avant de verrouiller les produits, et dépasseraient ensemble ce qui est retournable.
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("order", request.orderId()));
        requireReturnable(order);

        Map<Long, Integer> soldQuantities = new LinkedHashMap<>();
        Map<Long, BigDecimal> soldTotals = new LinkedHashMap<>();
        Map<Long, String> productNames = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            soldQuantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            soldTotals.merge(item.getProduct().getId(), item.getTotalPrice(), BigDecimal::add);
            productNames.putIfAbsent(item.getProduct().getId(), item.getProduct().getName());
        }
        Map<Long, Integer> alreadyReturned = returnedQuantitiesByProduct(order.getId());

        // Vérification de l'ensemble avant d'écrire quoi que ce soit : on additionne les
        // quantités par produit, sinon deux lignes avec le même article pourraient chacune être
        // valides mais dépasser ensemble ce qui reste retournable.
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (StockReturnItemRequest line : request.items()) {
            requested.merge(line.productId(), line.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            Integer sold = soldQuantities.get(entry.getKey());
            if (sold == null) {
                throw BusinessException.of("return.product.notInOrder",
                        "Un des articles retournés ne figure pas sur la commande "
                                + order.getOrderNumber(),
                        order.getOrderNumber());
            }
            int returnable = Math.max(0, sold - alreadyReturned.getOrDefault(entry.getKey(), 0));
            if (entry.getValue() > returnable) {
                String name = productNames.getOrDefault(entry.getKey(), String.valueOf(entry.getKey()));
                throw BusinessException.of("return.quantity.exceedsReturnable",
                        "Quantité retournée supérieure au retournable pour « " + name
                                + " » : " + entry.getValue() + " demandé(s), " + returnable + " possible(s).",
                        name, entry.getValue(), returnable);
            }
        }

        Invoice invoice = invoiceRepository.findByOrderId(order.getId()).orElse(null);

        StockReturn stockReturn = new StockReturn();
        stockReturn.setOrder(order);
        stockReturn.setInvoice(invoice);
        stockReturn.setNotes(request.notes());
        currentUser().ifPresent(stockReturn::setCreatedBy);

        // Le numéro est généré à l'enregistrement, et les mouvements de stock doivent l'avoir
        // comme référence : on enregistre donc d'abord l'en-tête, puis les lignes.
        stockReturn.setReturnNumber(documentNumberService.next(DocumentNumberService.DocumentType.RETURN));

        StockReturn saved = stockReturnRepository.save(stockReturn);

        // Même prix de base que dans l'écran de saisie : ce que le client a payé, remise globale
        // comprise. On ne doit jamais rembourser plus que ce qui a été payé.
        BigDecimal paidRatio = paidRatio(order, invoice);

        int totalQuantity = 0;
        BigDecimal refundTotal = BigDecimal.ZERO;
        for (StockReturnItemRequest line : request.items()) {
            BigDecimal paidLineTotal = paidLineTotal(soldTotals.get(line.productId()), paidRatio);
            int soldQuantity = soldQuantities.get(line.productId());
            StockReturnItem item = applyLine(saved, order, line,
                    netUnitPrice(paidLineTotal, soldQuantity),
                    refundShare(paidLineTotal, soldQuantity, line.quantity()));
            saved.getItems().add(item);
            totalQuantity += item.getQuantity();
            refundTotal = refundTotal.add(item.getRefundAmount());
        }

        saved.setTotalQuantity(totalQuantity);
        saved.setRefundAmount(refundTotal);
        stockReturnRepository.save(saved);

        logActivity(saved, order);
        return saved;
    }

    /**
     * Applique une ligne de retour : l'article rendu revient en stock, puis on applique l'effet
     * du traitement choisi. Voir {@link StockReturnItem.ReturnTreatment} pour le détail de
     * chaque valeur.
     */
    private StockReturnItem applyLine(StockReturn stockReturn, Order order,
                                      StockReturnItemRequest line, BigDecimal unitPrice,
                                      BigDecimal refundAmount) {
        Product product = productRepository.findByIdForUpdate(line.productId())
                .orElseThrow(() -> new ResourceNotFoundException("product", line.productId()));

        recordMovement(product, StockMovement.MovementType.RETURN, line.quantity(),
                "Retour " + stockReturn.getReturnNumber() + " - commande " + order.getOrderNumber()
                        + " (" + line.reason() + ")",
                stockReturn.getReturnNumber());

        StockReturnItem item = new StockReturnItem();
        item.setStockReturn(stockReturn);
        item.setProduct(product);
        item.setQuantity(line.quantity());
        item.setUnitPrice(unitPrice);
        item.setReason(line.reason());
        item.setTreatment(line.treatment());

        if (line.treatment() == StockReturnItem.ReturnTreatment.REFUND) {
            item.setRefundAmount(refundAmount);
        }

        if (line.treatment() == StockReturnItem.ReturnTreatment.EXCHANGE) {
            // Par défaut, échange avec le même article : c'est le cas le plus courant (un
            // article défectueux remplacé par le même). Le produit rendu vient d'être remis en
            // stock, donc la sortie du remplaçant se fait sur un stock déjà à jour.
            Long replacementId = line.replacementProductId() != null
                    ? line.replacementProductId()
                    : line.productId();
            Product replacement = replacementId.equals(product.getId())
                    ? product
                    : productRepository.findByIdForUpdate(replacementId)
                            .orElseThrow(() -> new ResourceNotFoundException("product", replacementId));

            if (replacement.getStockQuantity() < line.quantity()) {
                throw new InsufficientStockException(replacement.getName(),
                        replacement.getStockQuantity(), line.quantity());
            }
            recordMovement(replacement, StockMovement.MovementType.STOCK_OUT, -line.quantity(),
                    "Échange - retour " + stockReturn.getReturnNumber()
                            + " (remplace " + product.getName() + ")",
                    stockReturn.getReturnNumber());
            item.setReplacementProduct(replacement);
        }

        return item;
    }

    /* ---------- Consultation des retours ---------- */

    @Transactional(readOnly = true)
    public Page<StockReturn> searchReturns(Long orderId, LocalDateTime start, LocalDateTime end,
                                           String search, Pageable pageable) {
        Specification<StockReturn> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderId != null) {
                predicates.add(cb.equal(root.get("order").get("id"), orderId));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                var order = root.join("order", JoinType.LEFT);
                var client = order.join("client", JoinType.LEFT);
                var invoice = root.join("invoice", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("returnNumber")), like),
                        cb.like(cb.lower(order.get("orderNumber")), like),
                        cb.like(cb.lower(invoice.get("invoiceNumber")), like),
                        cb.like(cb.lower(client.get("firstName")), like),
                        cb.like(cb.lower(client.get("lastName")), like),
                        cb.like(cb.lower(client.get("company")), like),
                        cb.like(cb.lower(root.get("notes")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return stockReturnRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<StockReturn> getReturnById(Long id) {
        return stockReturnRepository.findDetailedById(id);
    }

    /* ---------- Outils ---------- */

    /**
     * Enregistre un mouvement de stock sur un produit DÉJÀ verrouillé et met à jour sa
     * quantité. Même principe que {@code OrderService.recordMovement} : le verrou reste à
     * l'appelant, et le mouvement garde le stock avant/après pour pouvoir vérifier.
     *
     * @param delta variation du stock (positif = entrée, négatif = sortie)
     */
    private void recordMovement(Product lockedProduct, StockMovement.MovementType type,
                                int delta, String reason, String reference) {
        int previousStock = lockedProduct.getStockQuantity();
        int newStock = previousStock + delta;

        StockMovement movement = new StockMovement();
        movement.setProduct(lockedProduct);
        movement.setType(type);
        movement.setQuantity(Math.abs(delta));
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason);
        movement.setReference(reference);
        currentUser().ifPresent(movement::setUser);

        stockMovementRepository.save(movement);

        lockedProduct.setStockQuantity(newStock);
        productRepository.save(lockedProduct);
    }

    private Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return userRepository.findById(user.getId());
        }
        return Optional.empty();
    }

    // Si le journal d'activité échoue, le retour ne doit pas échouer pour autant.
    private void logActivity(StockReturn stockReturn, Order order) {
        try {
            currentUser().ifPresent(user -> activityLogService.logActivity(
                    user.getId(), ActivityLog.ActionType.STOCK_IN, "StockReturn", stockReturn.getId(),
                    "Retour " + stockReturn.getReturnNumber() + " sur la commande " + order.getOrderNumber()
                            + " - " + stockReturn.getTotalQuantity() + " article(s)"
                            + (stockReturn.getRefundAmount().signum() > 0
                                    ? ", remboursement " + stockReturn.getRefundAmount() + " €" : ""),
                    null, null));
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }
}
