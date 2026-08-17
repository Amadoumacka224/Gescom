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
 * Gestion des retours clients, adossée à la vente d'origine.
 *
 * Le module part toujours d'une vente identifiée : {@link #lookup(String)} retrouve la commande
 * depuis un numéro de commande OU de facture et renvoie ses lignes avec la quantité encore
 * retournable. {@link #createReturn(StockReturnRequest)} rejoue ensuite ce contrôle côté serveur —
 * l'écran borne la saisie, le service la garantit — puis applique les mouvements de stock et
 * enregistre le document de retour.
 *
 * Les quantités sont modifiées sous verrou pessimiste ({@code findByIdForUpdate}), comme dans
 * {@link OrderService} : un retour et une confirmation de commande peuvent viser le même produit
 * au même instant. Pour la même raison, l'écriture du mouvement passe par un helper local plutôt
 * que par {@link StockService}, dont les méthodes rechargeraient le produit sans le verrou.
 *
 * L'enregistrement verrouille en plus la commande elle-même : le retournable se calcule sur le
 * cumul des retours de la vente, un contrôle que le verrou des produits ne couvre pas.
 */
@Service
@Transactional
public class StockReturnService {

    private static final Logger log = LoggerFactory.getLogger(StockReturnService.class);

    /**
     * Statuts de commande dont la marchandise est sortie du stock (le décrément a lieu à la
     * confirmation) : ce sont les seuls qui puissent donner lieu à un retour. Une commande en
     * attente n'a rien sorti, une commande annulée a déjà tout restitué.
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

    /** Attribution des numeros de documents : suite par entreprise et par annee. */
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
     * Retrouve la vente désignée par un numéro de commande ou de facture et prépare ses lignes
     * retournables. La recherche est insensible à la casse et tente les deux registres : côté
     * comptoir, le client tend indifféremment son bon de commande ou sa facture.
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

        // La facture est toujours relue depuis la commande : qu'on soit entré par l'un ou l'autre
        // numéro, la réponse décrit la même vente de la même façon.
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

    /** Refuse d'emblée une vente dont la marchandise n'est pas (ou n'est plus) chez le client. */
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
     * Lignes de la vente avec, pour chacune, ce qui reste retournable.
     * Les quantités sont agrégées par produit : une commande peut porter deux fois le même
     * article, or c'est bien un seul solde retournable qui doit être présenté.
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
     * Part du montant de ligne que le client a réellement payée, une fois la remise globale
     * de la vente appliquée.
     *
     * Les remises se posent à deux endroits : celle de la ligne est déjà déduite de
     * {@code OrderItem.totalPrice}, mais celle de la vente entière est un montant global, porté
     * par la commande puis repris — cumulé à l'éventuelle remise de facturation — par la
     * facture. Rembourser le total de ligne brut rendrait donc plus que ce qui a été encaissé.
     * On répartit la remise au prorata des lignes, l'assiette étant leur somme, c'est-à-dire
     * exactement ce sur quoi elle a été calculée.
     *
     * La facture prime quand elle existe : sa remise contient déjà celle de la commande.
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

        // Ratio à 6 décimales : c'est un facteur intermédiaire, les montants qui en découlent
        // sont arrondis au centime une seule fois, au moment de les exposer.
        return net.divide(gross, 6, RoundingMode.HALF_UP);
    }

    /** Total de ligne réellement payé : le total net de remise de ligne, au prorata du global. */
    private BigDecimal paidLineTotal(BigDecimal lineTotal, BigDecimal paidRatio) {
        if (lineTotal == null) return BigDecimal.ZERO;
        return lineTotal.multiply(paidRatio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Prix unitaire réellement payé : le total payé de la ligne (toutes remises déduites)
     * rapporté à la quantité. C'est ce montant, et non le tarif courant du produit, qui sert
     * de base à un remboursement.
     *
     * Valeur d'affichage : elle est arrondie au centime, donc indicative dès que le total ne
     * se divise pas en parts entières. Le montant remboursé, lui, vient de
     * {@link #refundShare(BigDecimal, int, int)}.
     */
    private BigDecimal netUnitPrice(BigDecimal lineTotal, int quantity) {
        if (quantity <= 0 || lineTotal == null) return BigDecimal.ZERO;
        return lineTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    /**
     * Part du total payé qui revient aux unités rendues : {@code total × rendues / vendues},
     * arrondie une seule fois, à la fin.
     *
     * Multiplier le prix unitaire déjà arrondi par la quantité perdrait un centime dès que le
     * total ne se divise pas en parts entières : une ligne de 10,00 € pour 3 unités donne
     * 3,33 € l'unité, et son retour intégral rembourserait 9,99 € — le client ne récupérerait
     * pas ce qu'il a versé. En partant du total, un retour intégral rend exactement le total,
     * et deux retours partiels qui épuisent la ligne se recomposent (6,67 + 3,33).
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
     * Enregistre un retour : contrôle les quantités, applique les mouvements de stock propres à
     * chaque traitement, puis persiste le document. Tout se joue dans une seule transaction —
     * un retour partiellement appliqué laisserait un stock faux.
     */
    public StockReturn createReturn(StockReturnRequest request) {
        // La commande est verrouillée dès l'entrée : le contrôle des quantités porte sur le
        // cumul des retours déjà enregistrés sur cette vente, que le verrou des produits ne
        // protège pas. Sans lui, deux retours simultanés liraient tous deux « rien de rendu »
        // avant de verrouiller les produits, et dépasseraient ensemble le retournable.
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

        // Contrôle d'ensemble avant toute écriture : les quantités sont cumulées par produit,
        // sans quoi deux lignes portant le même article pourraient, chacune valide, dépasser
        // ensemble le solde retournable.
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

        // Le numéro est généré à la persistance, or les mouvements de stock doivent le porter en
        // référence : on enregistre donc l'entête d'abord, les lignes ensuite.
        stockReturn.setReturnNumber(documentNumberService.next(DocumentNumberService.DocumentType.RETURN));

        StockReturn saved = stockReturnRepository.save(stockReturn);

        // Même base de prix que l'écran de saisie : ce que le client a payé, remise globale de
        // la vente comprise. Le montant remboursé ne doit jamais dépasser l'encaissé.
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
     * Applique une ligne de retour : réintégration au stock de l'article rendu, puis effet propre
     * au traitement retenu. Voir {@link StockReturnItem.ReturnTreatment} pour la sémantique de
     * chaque valeur.
     */
    private StockReturnItem applyLine(StockReturn stockReturn, Order order,
                                      StockReturnItemRequest line, BigDecimal unitPrice,
                                      BigDecimal refundAmount) {
        Product product = productRepository.findByIdForUpdate(line.productId())
                .orElseThrow(() -> new ResourceNotFoundException("product", line.productId()));

        recordMovement(product, StockMovement.MovementType.RETURN, line.quantity(),
                "Retour " + stockReturn.getReturnNumber() + " — commande " + order.getOrderNumber()
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
            // Échange à l'identique par défaut : c'est le cas courant (unité défectueuse
            // remplacée par la même référence). Le produit rendu vient d'être réintégré, la
            // sortie du remplaçant se fait donc sur un stock déjà à jour.
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
                    "Échange — retour " + stockReturn.getReturnNumber()
                            + " (remplace " + product.getName() + ")",
                    stockReturn.getReturnNumber());
            item.setReplacementProduct(replacement);
        }

        return item;
    }

    /* ---------- Consultation du registre ---------- */

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

    /* ---------- Utilitaires ---------- */

    /**
     * Enregistre un mouvement de stock sur un produit DÉJÀ verrouillé et applique la nouvelle
     * quantité. Même contrat que {@code OrderService.recordMovement} : le verrou pessimiste reste
     * détenu par l'appelant, et le mouvement fige le stock avant/après pour rester auditable.
     *
     * @param delta variation appliquée au stock (positif = entrée, négatif = sortie)
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

    // L'échec du journal d'audit ne doit jamais faire échouer le retour lui-même.
    private void logActivity(StockReturn stockReturn, Order order) {
        try {
            currentUser().ifPresent(user -> activityLogService.logActivity(
                    user.getId(), ActivityLog.ActionType.STOCK_IN, "StockReturn", stockReturn.getId(),
                    "Retour " + stockReturn.getReturnNumber() + " sur la commande " + order.getOrderNumber()
                            + " — " + stockReturn.getTotalQuantity() + " article(s)"
                            + (stockReturn.getRefundAmount().signum() > 0
                                    ? ", remboursement " + stockReturn.getRefundAmount() + " €" : ""),
                    null, null));
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }
}
