package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.InsufficientStockException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.dto.order.OrderFilterOptions;
import com.gescom.backend.dto.order.OrderSearchCriteria;
import com.gescom.backend.dto.order.OrderSummary;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import com.gescom.backend.security.CashierScope;
import com.gescom.backend.security.OwnershipViolationException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service des commandes : création, modification, changements de statut et annulation.
 * C'est ici que se fait la gestion du stock : chaque opération qui touche aux quantités
 * utilise un verrou pessimiste (findByIdForUpdate) pour éviter les problèmes quand plusieurs
 * personnes travaillent en même temps.
 * Toutes les méthodes sont dans une transaction (@Transactional sur la classe).
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockReturnRepository stockReturnRepository;
    private final ActivityLogService activityLogService;
    private final CashierScope cashierScope;

    /** Generation des numeros de documents : une suite par entreprise et par annee. */
    private final DocumentNumberService documentNumberService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, StockMovementRepository stockMovementRepository,
                        InvoiceRepository invoiceRepository, StockReturnRepository stockReturnRepository,
                        ActivityLogService activityLogService, CashierScope cashierScope,
                              DocumentNumberService documentNumberService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.invoiceRepository = invoiceRepository;
        this.stockReturnRepository = stockReturnRepository;
        this.activityLogService = activityLogService;
        this.cashierScope = cashierScope;
        this.documentNumberService = documentNumberService;
    }

    /**
     * Commandes d'un caissier sur une journée [start, end), avec lignes et client déjà chargés.
     * Sert aux calculs du tableau de bord caisse (avant on faisait un getAllOrders() filtré en mémoire).
     */
    @Transactional(readOnly = true)
    public List<Order> getDayOrdersForCashier(Long userId, LocalDateTime start, LocalDateTime end) {
        return orderRepository.findDayOrdersForCashier(userId, start, end);
    }

    /**
     * Commandes de tous les caissiers sur une journée [start, end), avec lignes, client et
     * créateur déjà chargés. Sert aux calculs du suivi des caisses.
     */
    @Transactional(readOnly = true)
    public List<Order> getDayOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findDayOrders(start, end);
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Total net d'une ligne : prix unitaire × quantité, moins la remise (en %).
     * La remise est limitée entre 0 et 100 et le résultat arrondi à 2 décimales (HALF_UP).
     */
    private BigDecimal computeLineTotal(BigDecimal unitPrice, Integer quantity, BigDecimal discountPercent) {
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal pct = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        if (pct.signum() < 0) pct = BigDecimal.ZERO;
        if (pct.compareTo(HUNDRED) > 0) pct = HUNDRED;
        BigDecimal net = gross.multiply(HUNDRED.subtract(pct)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return net;
    }

    /**
     * Applique la remise globale (en euros) et calcule le net à facturer. On refuse une remise
     * plus grande que le total : ça donnerait une commande (puis une facture) négative,
     * impossible à payer. Pas de TVA sur la commande, elle est calculée à la facturation.
     */
    private void applyGlobalDiscount(Order order, BigDecimal discount) {
        BigDecimal applied = discount != null ? discount : BigDecimal.ZERO;
        if (applied.compareTo(order.getTotalAmount()) > 0) {
            throw BusinessException.of("order.discount.exceedsTotal",
                    "La remise (" + applied + " €) dépasse le total de la commande ("
                            + order.getTotalAmount() + " €)",
                    applied, order.getTotalAmount());
        }
        order.setDiscount(applied);
        order.setFinalAmount(order.getTotalAmount().subtract(applied));
    }

    /** Id de l'utilisateur connecté, pris dans le contexte de sécurité (null si anonyme). */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    // Enregistre une action dans le journal d'activité. Si le log échoue, l'opération ne doit
    // pas échouer pour autant : on écrit juste un warning.
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

    /**
     * Enregistre un mouvement de stock sur un produit DÉJÀ verrouillé (findByIdForUpdate) et
     * met à jour sa quantité. Le verrou reste à l'appelant ; on ne passe pas par
     * StockService.removeStock/addStock car ils rechargeraient le produit sans verrou. Le
     * mouvement garde le stock avant/après pour pouvoir vérifier et annuler.
     *
     * @param delta variation du stock (négatif = sortie, positif = entrée)
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

        Long userId = getCurrentUserId();
        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        stockMovementRepository.save(movement);

        lockedProduct.setStockQuantity(newStock);
        productRepository.save(lockedProduct);
    }

    /**
     * Toutes les commandes que l'appelant peut voir : toute l'entreprise pour un ADMIN,
     * seulement ses ventes pour un caissier ({@link CashierScope#restrictedUserId()} vaut null
     * dans le premier cas, ce qui désactive le filtre).
     */
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        // Tout est chargé en une requête (client, créateur, lignes, produits) pour éviter le
        // N+1 dans le mapping des listes et dans les calculs du tableau de bord.
        return orderRepository.findAllWithDetails(cashierScope.restrictedUserId());
    }

    /**
     * Une page de commandes, filtrée et triée en base, chaque ligne chargée en entier.
     *
     * <h2>Pourquoi deux requêtes</h2>
     *
     * Une seule requête ne suffit pas, et ce n'est pas juste de l'optimisation. La liste a
     * besoin des ARTICLES de chaque commande (le tableau affiche leur nombre, et la recherche
     * se fait aussi sur leur nom), et pour les charger il faut un {@code JOIN FETCH} sur une
     * collection. Or une requête avec ce type de fetch ne peut pas être paginée en base : la
     * jointure multiplie les lignes, et un {@code LIMIT 50} couperait une commande au milieu
     * de ses articles. Hibernate le sait, donc il charge TOUT et pagine en mémoire, avec le
     * warning {@code HHH90003004}. On consommerait autant qu'avant, plus le découpage : une
     * pagination qui ne sert à rien.
     *
     * Donc la première requête sélectionne les commandes SANS leurs articles : pas de
     * collection jointe, donc un vrai {@code LIMIT/OFFSET} et un {@code COUNT} juste. La
     * deuxième charge le détail seulement pour les commandes de la page.
     *
     * <h2>Le montant filtré n'est pas une colonne</h2>
     *
     * L'écran affiche et filtre un montant TTC qui n'existe pas en base : si la commande est
     * facturée, c'est le total de la facture (TVA et remise fixées à ce moment) ; sinon, ce
     * montant n'existe pas et l'écran l'estime avec le taux des paramètres. On refait la même
     * règle ici en SQL, sinon le filtre par montant exclurait des lignes dont le montant
     * affiché est pourtant dans la fourchette, et on croirait à un bug.
     */
    @Transactional(readOnly = true)
    public Page<Order> searchOrders(OrderSearchCriteria criteria, BigDecimal taxRate, Pageable pageable) {
        return searchOrders(criteria, taxRate, pageable, null);
    }

    /**
     * Version triée sur le montant TTC affiché.
     *
     * Comme ce montant n'est pas une colonne (voir la méthode au-dessus), aucun {@code sort=}
     * de Spring Data ne peut le désigner : il faut mettre l'ORDER BY directement sur le calcul.
     * {@code payableDirection} donne le sens demandé ; le {@code pageable} reçu doit alors être
     * NON trié, sinon Spring Data remplacerait cet ordre par le sien.
     *
     * Trier sur {@code finalAmount} aurait été plus simple mais faux : dès qu'il y a une
     * facture, l'ordre ne correspondrait plus aux montants affichés dans la colonne.
     */
    @Transactional(readOnly = true)
    public Page<Order> searchOrders(OrderSearchCriteria criteria, BigDecimal taxRate, Pageable pageable,
                                    Sort.Direction payableDirection) {
        Page<Order> idPage = orderRepository.findAll(
                buildFilter(criteria, taxRate, payableDirection), pageable);
        List<Long> ids = idPage.getContent().stream().map(Order::getId).toList();
        if (ids.isEmpty()) {
            return idPage;
        }

        // On remet dans l'ordre de la page demandée : `IN (...)` ne garantit aucun ordre, et si
        // on laissait faire la base, les lignes changeraient de place à chaque rafraîchissement.
        Map<Long, Order> byId = orderRepository.findAllWithDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        List<Order> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();

        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    /**
     * Commandes prêtes à livrer : facturées et sans livraison.
     *
     * Sert pour la liste du formulaire de livraison, qui calculait avant cette liste lui-même
     * à partir de toutes les livraisons. Maintenant elles sont paginées, donc la liste serait
     * incomplète.
     */
    @Transactional(readOnly = true)
    public List<Order> getDeliverableOrders() {
        return orderRepository.findDeliverable(
                Order.OrderStatus.INVOICED, cashierScope.restrictedUserId());
    }

    /** Nombre de commandes par statut, sur ce que l'appelant peut voir (voir {@link OrderSummary}). */
    @Transactional(readOnly = true)
    public OrderSummary getSummary() {
        OrderRepository.OrderSummaryView v = orderRepository.summaryFor(
                cashierScope.restrictedUserId(),
                Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED, Order.OrderStatus.INVOICED,
                Order.OrderStatus.DELIVERED, Order.OrderStatus.CANCELED);
        return new OrderSummary(v.getTotal(), v.getPending(), v.getConfirmed(),
                v.getInvoiced(), v.getDelivered(), v.getCanceled());
    }

    /** Utilisateurs et villes proposés dans les filtres (voir {@link OrderFilterOptions}). */
    @Transactional(readOnly = true)
    public OrderFilterOptions getFilterOptions() {
        Long restrictedUserId = cashierScope.restrictedUserId();
        List<OrderFilterOptions.Operator> operators = orderRepository.findDistinctOperators(restrictedUserId)
                .stream()
                .map(v -> new OrderFilterOptions.Operator(v.getId(), operatorLabel(v)))
                .toList();
        return new OrderFilterOptions(operators, orderRepository.findDistinctClientCities(restrictedUserId));
    }

    /** « Prénom Nom », sinon le login (comme faisait l'écran). */
    private String operatorLabel(OrderRepository.OperatorView v) {
        String composed = java.util.stream.Stream.of(v.getFirstName(), v.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return composed.isBlank() ? v.getUsername() : composed;
    }

    private Specification<Order> buildFilter(OrderSearchCriteria c, BigDecimal taxRate,
                                             Sort.Direction payableDirection) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Tri sur le montant TTC calculé. Mis ici car le calcul n'existe que dans cette
            // requête ; ignoré pour la requête de comptage, qui renvoie un Long et n'a pas besoin
            // d'ORDER BY (PostgreSQL le refuserait d'ailleurs sur un COUNT).
            if (payableDirection != null && !isCountQuery(query)) {
                Expression<BigDecimal> payable = payableAmount(root, query, cb, taxRate);
                query.orderBy(payableDirection == Sort.Direction.ASC
                                ? cb.asc(payable) : cb.desc(payable),
                        // Deuxième critère pour départager : deux commandes avec le même montant
                        // n'auraient pas d'ordre fixe et changeraient de page au rafraîchissement.
                        cb.desc(root.get("id")));
            }

            // Limite du caissier à ses ventes, comme dans findAllWithDetails : ça doit être fait
            // EN BASE, sinon on récupérerait une page de toute l'entreprise filtrée ensuite, et
            // le caissier verrait des pages à moitié vides et un total faux.
            Long restrictedUserId = cashierScope.restrictedUserId();
            if (restrictedUserId != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), restrictedUserId));
            }

            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.clientId() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), c.clientId()));
            }
            if (c.clientType() != null) {
                predicates.add(cb.equal(root.get("client").get("type"), c.clientType()));
            }
            if (c.city() != null && !c.city().isBlank()) {
                predicates.add(cb.equal(root.get("client").get("city"), c.city()));
            }
            if (c.createdById() != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), c.createdById()));
            }
            if (c.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.dateFrom().atStartOfDay()));
            }
            if (c.dateTo() != null) {
                // Borne de fin exclue au lendemain minuit : createdAt a une heure, donc une
                // commande passée à 14 h le dernier jour serait sinon exclue.
                predicates.add(cb.lessThan(root.get("createdAt"), c.dateTo().plusDays(1).atStartOfDay()));
            }
            if (c.onlyDiscounted()) {
                predicates.add(cb.greaterThan(cb.coalesce(root.get("discount"), BigDecimal.ZERO), BigDecimal.ZERO));
            }
            if (c.notes() != null && !c.notes().isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("notes"), "")),
                        "%" + c.notes().toLowerCase().trim() + "%"));
            }

            // Filtre sur un article ou une catégorie : avec un EXISTS plutôt qu'une jointure,
            // qui dupliquerait la commande pour chaque ligne correspondante et fausserait le
            // COUNT de la pagination.
            if (c.productId() != null) {
                predicates.add(existsItem(root, query, cb, item ->
                        cb.equal(item.get("product").get("id"), c.productId())));
            }
            if (c.categoryId() != null) {
                predicates.add(existsItem(root, query, cb, item ->
                        cb.equal(item.get("product").get("category").get("id"), c.categoryId())));
            }

            addPaymentPredicate(c, root, query, cb, predicates);
            addAmountPredicate(c, root, query, cb, taxRate, predicates);
            addFullTextPredicate(c, root, query, cb, predicates);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** {@code EXISTS (SELECT 1 FROM OrderItem i WHERE i.order = o AND <condition>)}. */
    private Predicate existsItem(Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                 Function<Root<OrderItem>, Predicate> condition) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<OrderItem> item = sub.from(OrderItem.class);
        sub.select(cb.literal(1))
           .where(cb.and(cb.equal(item.get("order"), root), condition.apply(item)));
        return cb.exists(sub);
    }

    /**
     * Statut de la facture liée.
     *
     * « Pas encore facturée » veut dire PAS de facture active, ce n'est pas un statut : une
     * commande dont la facture a été annulée revient dans ce cas, comme sur l'écran.
     */
    private void addPaymentPredicate(OrderSearchCriteria c, Root<Order> root, CriteriaQuery<?> query,
                                     CriteriaBuilder cb, List<Predicate> predicates) {
        if (c.notInvoiced()) {
            predicates.add(cb.not(existsLiveInvoice(root, query, cb, null)));
        } else if (c.payment() != null) {
            predicates.add(existsLiveInvoice(root, query, cb, c.payment()));
        }
    }

    /**
     * {@code EXISTS} d'une facture non annulée sur la commande, avec un statut donné si besoin.
     * Une facture annulée ne compte jamais.
     */
    private Predicate existsLiveInvoice(Root<Order> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                        Invoice.InvoiceStatus status) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<Invoice> invoice = sub.from(Invoice.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(invoice.get("order"), root));
        conditions.add(cb.notEqual(invoice.get("status"), Invoice.InvoiceStatus.CANCELED));
        if (status != null) {
            conditions.add(cb.equal(invoice.get("status"), status));
        }
        sub.select(cb.literal(1)).where(cb.and(conditions.toArray(new Predicate[0])));
        return cb.exists(sub);
    }

    /**
     * Filtre min/max sur le montant TTC affiché, recalculé en SQL comme sur l'écran : total de
     * la facture active s'il y en a une, sinon net HT + TVA au taux des paramètres.
     */
    private void addAmountPredicate(OrderSearchCriteria c, Root<Order> root, CriteriaQuery<?> query,
                                    CriteriaBuilder cb, BigDecimal taxRate, List<Predicate> predicates) {
        if (c.amountMin() == null && c.amountMax() == null) {
            return;
        }
        Expression<BigDecimal> payable = payableAmount(root, query, cb, taxRate);
        if (c.amountMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(payable, c.amountMin()));
        }
        if (c.amountMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(payable, c.amountMax()));
        }
    }

    /**
     * Montant TTC affiché, recalculé en SQL : total de la facture active s'il y en a une,
     * sinon net HT + TVA au taux des paramètres.
     *
     * Une seule définition, utilisée pour le filtre et pour le tri. Avec deux copies, on
     * finirait par avoir des différences, et ça se verrait au pire endroit : une ligne triée
     * à une place alors que le filtre l'exclut.
     */
    private Expression<BigDecimal> payableAmount(Root<Order> root, CriteriaQuery<?> query,
                                                 CriteriaBuilder cb, BigDecimal taxRate) {
        Subquery<BigDecimal> billed = query.subquery(BigDecimal.class);
        Root<Invoice> invoice = billed.from(Invoice.class);
        billed.select(invoice.get("totalAmount"))
              .where(cb.and(
                      cb.equal(invoice.get("order"), root),
                      cb.notEqual(invoice.get("status"), Invoice.InvoiceStatus.CANCELED)));

        BigDecimal multiplier = BigDecimal.ONE.add(
                (taxRate == null ? BigDecimal.ZERO : taxRate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        Expression<BigDecimal> estimated = cb.prod(
                cb.coalesce(root.get("finalAmount"), BigDecimal.ZERO), multiplier);
        return cb.coalesce(billed, estimated);
    }

    /** La requête de comptage générée par Spring Data à partir de la spécification renvoie un {@code Long}. */
    private boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return Long.class.equals(type) || long.class.equals(type);
    }

    /**
     * Recherche libre. Un seul champ cherche dans le numéro, le client (nom, société…),
     * l'utilisateur, les notes et les articles : on peut retrouver une vente avec ce dont on
     * se souvient, sans savoir dans quel champ chercher.
     */
    private void addFullTextPredicate(OrderSearchCriteria c, Root<Order> root, CriteriaQuery<?> query,
                                      CriteriaBuilder cb, List<Predicate> predicates) {
        if (c.q() == null || c.q().isBlank()) {
            return;
        }
        String pattern = "%" + c.q().toLowerCase().trim() + "%";
        // LEFT JOIN explicites, et ce n'est pas juste du style, c'est une correction :
        // root.get("client") fait un INNER JOIN, qui exclut les commandes sans client. Or
        // Order.client peut être null exprès (vente sans client). Une vente comme ça n'était
        // plus trouvée par la recherche (vu en testant : CMD-2026-0035, sans client, ne
        // sortait sur aucun mot).
        Join<Object, Object> client = root.join("client", JoinType.LEFT);
        Join<Object, Object> createdBy = root.join("createdBy", JoinType.LEFT);

        Predicate onOrder = cb.or(
                like(cb, root.get("orderNumber"), pattern),
                like(cb, root.get("notes"), pattern),
                like(cb, client.get("firstName"), pattern),
                like(cb, client.get("lastName"), pattern),
                like(cb, client.get("email"), pattern),
                like(cb, client.get("phone"), pattern),
                like(cb, client.get("company"), pattern),
                like(cb, client.get("city"), pattern),
                like(cb, createdBy.get("username"), pattern),
                like(cb, createdBy.get("firstName"), pattern),
                like(cb, createdBy.get("lastName"), pattern));

        Predicate onItems = existsItem(root, query, cb, item -> cb.or(
                like(cb, item.get("product").get("name"), pattern),
                like(cb, item.get("product").get("code"), pattern),
                like(cb, item.get("product").get("barcode"), pattern)));

        predicates.add(cb.or(onOrder, onItems));
    }

    /** COALESCE avant LOWER : avec une colonne null, la comparaison échouerait au lieu de donner faux. */
    private Predicate like(CriteriaBuilder cb, Path<?> path, String pattern) {
        return cb.like(cb.lower(cb.coalesce(path.as(String.class), "")), pattern);
    }

    /**
     * Lecture d'une commande. Si elle n'est pas au caissier, on fait comme si elle n'existait
     * pas au lieu de refuser : un caissier ne doit pas pouvoir deviner, avec la différence
     * entre 403 et 404, que les ventes de ses collègues existent ni combien il y en a.
     */
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return cashierScope.filterReadable(orderRepository.findById(id), o -> o);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        return cashierScope.filterReadable(orderRepository.findByOrderNumber(orderNumber), o -> o);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByClient(Long clientId) {
        return orderRepository.findByClientId(clientId, cashierScope.restrictedUserId());
    }

    /**
     * Ventes d'un utilisateur. Demander celles d'un autre que soi est déjà bloqué par le
     * {@code @PreAuthorize} du contrôleur ; on revérifie ici pour que la méthode reste sûre
     * peu importe qui l'appelle.
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        Long restricted = cashierScope.restrictedUserId();
        if (restricted != null && !restricted.equals(userId)) {
            throw new OwnershipViolationException();
        }
        return orderRepository.findByCreatedById(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status, cashierScope.restrictedUserId());
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetween(start, end, cashierScope.restrictedUserId());
    }

    /**
     * Crée une commande en brouillon (PENDING). On ne touche PAS au stock ici : il ne sera
     * diminué qu'à la confirmation (confirmOrder). On fixe les prix unitaires et on calcule
     * les totaux ; une vérification du stock en lecture seule (sans verrou ni modification)
     * refuse tout de suite un brouillon impossible, mais le vrai contrôle se fait à la
     * confirmation.
     */
    public Order createOrder(Order order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("user", "username", username));

        order.setCreatedBy(user);
        order.setStatus(Order.OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("product", item.getProduct().getId()));

            // Vérification juste indicative (le stock n'est pas réservé) : pour prévenir tout
            // de suite. La sortie de stock et le vrai contrôle sous verrou se font à la confirmation.
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), item.getQuantity());
            }

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(computeLineTotal(item.getUnitPrice(), item.getQuantity(), item.getDiscount()));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(order);
        }

        order.setTotalAmount(totalAmount);
        applyGlobalDiscount(order, order.getDiscount());

        // Numéro donné DANS cette transaction : le verrou n'est libéré qu'au commit, donc après
        // l'enregistrement de la vente. La suivante lira le bon maximum.
        order.setOrderNumber(documentNumberService.next(DocumentNumberService.DocumentType.ORDER));

        Order savedOrder = orderRepository.save(order);

        logActivity(ActivityLog.ActionType.SALE, "Order", savedOrder.getId(),
            "Création de la commande " + savedOrder.getOrderNumber() + " - Montant: " + savedOrder.getFinalAmount());

        return savedOrder;
    }

    /**
     * Confirme une commande (PENDING → CONFIRMED). C'est ici que le stock sort vraiment :
     * chaque produit est verrouillé (findByIdForUpdate), son stock vérifié puis diminué, et un
     * mouvement STOCK_OUT est enregistré. Si le stock ne suffit pas au moment de la
     * confirmation, rien n'est modifié.
     */
    public Order confirmOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
        cashierScope.requireAccess(order);

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw BusinessException.of("order.confirm.onlyPending",
                    "Seule une commande en attente peut être confirmée (statut actuel : "
                            + order.getStatus() + ")",
                    order.getStatus());
        }

        // Additionne les quantités par produit (une commande peut avoir 2 fois le même produit).
        java.util.Map<Long, Integer> quantityByProduct = new java.util.HashMap<>();
        for (OrderItem item : order.getItems()) {
            quantityByProduct.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }

        // Verrou + vérification avant de toucher au stock : on ne sort le stock que si toutes
        // les lignes sont possibles, pour ne pas confirmer à moitié.
        for (java.util.Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("product", entry.getKey()));
            if (product.getStockQuantity() < entry.getValue()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), entry.getValue());
            }
            recordMovement(product, StockMovement.MovementType.STOCK_OUT, -entry.getValue(),
                    "Vente - commande " + order.getOrderNumber(), order.getOrderNumber());
        }

        return transitionTo(order, Order.OrderStatus.CONFIRMED);
    }

    /**
     * Modifie une commande encore en brouillon. On ne peut modifier les articles qu'en PENDING :
     * une fois confirmée, les lignes sont figées (le stock est sorti et elles servent pour la
     * facture). Pas d'opération de stock ici, puisqu'une commande PENDING n'a jamais pris de
     * stock : on refixe juste les prix et on recalcule les totaux.
     */
    public Order updateOrder(Long id, Order updatedOrder) {
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
        cashierScope.requireAccess(existingOrder);

        if (existingOrder.getStatus() != Order.OrderStatus.PENDING) {
            throw BusinessException.of("order.update.onlyPending",
                    "Seule une commande en attente peut être modifiée. "
                            + "Une commande confirmée doit être annulée puis recréée (statut actuel : "
                            + existingOrder.getStatus() + ")",
                    existingOrder.getStatus());
        }

        // Mise à jour partielle : on ne remplace que les champs envoyés. Un appelant qui ne
        // gère que les articles (l'écran Commandes par exemple) ne doit pas remettre la remise
        // à zéro ni effacer les notes. Une chaîne vide, par contre, efface bien les notes
        // (c'est le seul moyen de les vider).
        if (updatedOrder.getNotes() != null) {
            existingOrder.setNotes(updatedOrder.getNotes().isBlank() ? null : updatedOrder.getNotes());
        }
        existingOrder.getItems().clear();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : updatedOrder.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("product", item.getProduct().getId()));

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(computeLineTotal(item.getUnitPrice(), item.getQuantity(), item.getDiscount()));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(existingOrder);
            existingOrder.getItems().add(item);
        }

        existingOrder.setTotalAmount(totalAmount);
        applyGlobalDiscount(existingOrder,
                updatedOrder.getDiscount() != null ? updatedOrder.getDiscount() : existingOrder.getDiscount());

        Order savedOrder = orderRepository.save(existingOrder);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Modification de la commande " + savedOrder.getOrderNumber());

        return savedOrder;
    }

    /**
     * Changement de statut à la main. Seuls les changements sans document lié sont autorisés
     * ici : confirmation (→ CONFIRMED, qui sort le stock) et annulation (→ CANCELED). Le
     * passage à INVOICED / DELIVERED se fait uniquement par la facturation et la livraison,
     * pour garantir que « INVOICED ⟺ une facture existe ».
     */
    public Order updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        if (newStatus == Order.OrderStatus.CONFIRMED) {
            return confirmOrder(id);
        }
        if (newStatus == Order.OrderStatus.CANCELED) {
            cancelOrder(id);
            return orderRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("order", id));
        }
        throw BusinessException.of("order.status.notDirectlySettable",
                "Changement de statut non autorisé directement vers " + newStatus
                        + " : la facturation et la livraison pilotent les statuts INVOICED et DELIVERED.",
                newStatus);
    }

    /**
     * Change le statut d'une commande après vérification par la machine à états.
     * C'est le seul endroit où on modifie Order.status : les autres services (InvoiceService,
     * DeliveryService) doivent passer par ici et pas faire un setStatus directement.
     */
    public Order transitionTo(Order order, Order.OrderStatus newStatus) {
        validateStatusTransition(order.getStatus(), newStatus);
        Order.OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Changement de statut de la commande " + savedOrder.getOrderNumber()
                + " : " + previous + " → " + newStatus);

        return savedOrder;
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus target) {
        if (current == target) {
            throw BusinessException.of("order.status.unchanged",
                    "La commande est déjà au statut " + current, current);
        }
        if (!current.canTransitionTo(target)) {
            throw BusinessException.of("order.status.invalidTransition",
                    "Transition de statut invalide : " + current + " → " + target, current, target);
        }
    }

    public void cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
        cashierScope.requireAccess(order);

        if (!order.getStatus().canTransitionTo(Order.OrderStatus.CANCELED)) {
            throw BusinessException.of("order.cancel.notAllowed",
                    "Impossible d'annuler une commande au statut " + order.getStatus(), order.getStatus());
        }

        // On n'annule pas une commande dont la facture est encore active, sinon on aurait une
        // facture active liée à une commande annulée. Il faut d'abord annuler la facture
        // (InvoiceService.cancelInvoice).
        invoiceRepository.findByOrderId(order.getId())
                .filter(inv -> inv.getStatus() != Invoice.InvoiceStatus.CANCELED)
                .ifPresent(inv -> {
                    throw BusinessException.of("order.cancel.invoiceAttached",
                            "Une facture est associée à cette commande - "
                                    + "annulez d'abord la facture " + inv.getInvoiceNumber(),
                            inv.getInvoiceNumber());
                });

        requireNoReturns(order, "order.cancel.returnAttached",
                "Un retour client a déjà été enregistré sur la commande " + order.getOrderNumber()
                        + " : l'annuler restituerait au stock des articles déjà rendus. "
                        + "Corrigez le stock par un ajustement.");

        // On remet le stock seulement s'il avait été pris (à la confirmation). Une commande
        // restée PENDING n'a jamais sorti de stock, rien à remettre. Le retour est enregistré (STOCK_IN).
        if (stockWasConsumed(order.getStatus())) {
            restoreStock(order, "Annulation - commande " + order.getOrderNumber());
        }

        order.setStatus(Order.OrderStatus.CANCELED);
        orderRepository.save(order);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", order.getId(),
            "Annulation de la commande " + order.getOrderNumber());
    }

    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
        cashierScope.requireAccess(order);

        requireNoReturns(order, "order.delete.returnAttached",
                "Un retour client est rattaché à la commande " + order.getOrderNumber()
                        + " : la supprimer effacerait la vente qui justifie ce retour.");

        // On remet le stock seulement s'il avait été pris et que la commande n'est pas déjà
        // annulée (dans ce cas le stock a déjà été remis lors de l'annulation).
        if (order.getStatus() != Order.OrderStatus.CANCELED && stockWasConsumed(order.getStatus())) {
            restoreStock(order, "Suppression - commande " + order.getOrderNumber());
        }

        String orderNumber = order.getOrderNumber();
        orderRepository.delete(order);

        logActivity(ActivityLog.ActionType.DELETE, "Order", id,
            "Suppression de la commande " + orderNumber);
    }

    /**
     * Refuse l'annulation ou la suppression s'il y a un retour client sur la vente.
     *
     * {@link #restoreStock} remet toute la quantité vendue : sur une commande dont une partie
     * a déjà été rendue, les mêmes articles seraient remis deux fois en stock. On ne peut pas
     * corriger ça en enlevant les quantités rendues : un échange à l'identique remet puis
     * ressort la marchandise, et ne doit donc rien enlever. Une vente qui a eu un retour se
     * règle avec un ajustement de stock, qui est lui aussi enregistré dans les mouvements.
     *
     * Même chose pour la suppression : la ligne de retour pointe vers la commande, la supprimer
     * casserait la contrainte d'intégrité et laisserait un retour sans vente.
     */
    private void requireNoReturns(Order order, String messageKey, String message) {
        if (stockReturnRepository.countByOrderId(order.getId()) > 0) {
            throw BusinessException.of(messageKey, message, order.getOrderNumber());
        }
    }

    /** Vrai si, avec ce statut, le stock a déjà été diminué (depuis la confirmation). */
    private boolean stockWasConsumed(Order.OrderStatus status) {
        return status == Order.OrderStatus.CONFIRMED
                || status == Order.OrderStatus.INVOICED
                || status == Order.OrderStatus.DELIVERED;
    }

    /** Remet en stock les quantités de chaque ligne, avec verrou, et enregistre un STOCK_IN. */
    private void restoreStock(Order order, String reason) {
        java.util.Map<Long, Integer> quantityByProduct = new java.util.HashMap<>();
        for (OrderItem item : order.getItems()) {
            quantityByProduct.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        for (java.util.Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("product", entry.getKey()));
            recordMovement(product, StockMovement.MovementType.STOCK_IN, entry.getValue(),
                    reason, order.getOrderNumber());
        }
    }
}
