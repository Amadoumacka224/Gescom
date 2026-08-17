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
 * Service métier des commandes : création, modification, transitions de statut et annulation.
 * Cœur de la gestion du stock — chaque opération qui touche aux quantités utilise un verrou
 * pessimiste (findByIdForUpdate) pour rester cohérente face aux accès concurrents.
 * Toutes les méthodes s'exécutent dans une transaction (@Transactional au niveau de la classe).
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

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, StockMovementRepository stockMovementRepository,
                        InvoiceRepository invoiceRepository, StockReturnRepository stockReturnRepository,
                        ActivityLogService activityLogService, CashierScope cashierScope) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.invoiceRepository = invoiceRepository;
        this.stockReturnRepository = stockReturnRepository;
        this.activityLogService = activityLogService;
        this.cashierScope = cashierScope;
    }

    /**
     * Commandes d'un caissier sur une journée [start, end), lignes et client déjà chargés.
     * Base des agrégats du tableau de bord caisse — remplace un getAllOrders() filtré en mémoire.
     */
    @Transactional(readOnly = true)
    public List<Order> getDayOrdersForCashier(Long userId, LocalDateTime start, LocalDateTime end) {
        return orderRepository.findDayOrdersForCashier(userId, start, end);
    }

    /**
     * Commandes de tous les caissiers sur une journée [start, end), lignes, client et créateur
     * déjà chargés. Base des agrégats de la supervision des caisses.
     */
    @Transactional(readOnly = true)
    public List<Order> getDayOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findDayOrders(start, end);
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Calcule le total net d'une ligne : prix unitaire × quantité, diminué de la remise (en %).
     * La remise est bornée à [0, 100] et le résultat arrondi à 2 décimales (HALF_UP).
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
     * Fixe la remise globale (en euros) et en déduit le net à facturer. Une remise supérieure au
     * total est refusée : elle produirait une commande — puis une facture — à montant négatif,
     * impossible à encaisser. La commande ne porte pas de TVA : elle est calculée à la facturation.
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

    /** Récupère l'id de l'utilisateur authentifié depuis le contexte de sécurité (null si anonyme). */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    // Journalise une action dans l'historique d'activité. L'échec du log ne doit jamais
    // faire échouer l'opération métier : l'exception est seulement tracée en warning.
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
     * applique la nouvelle quantité. Le verrou pessimiste reste détenu par l'appelant ; on ne
     * passe pas par StockService.removeStock/addStock car ceux-ci rechargeraient le produit
     * sans verrou. Le mouvement fige le stock avant/après pour rester auditable et réversible.
     *
     * @param delta variation appliquée au stock (négatif = sortie, positif = entrée)
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
     * Toutes les commandes du périmètre de l'appelant : l'entreprise entière pour un ADMIN,
     * ses seules ventes pour un caissier ({@link CashierScope#restrictedUserId()} vaut null
     * dans le premier cas et désactive alors le filtre).
     */
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        // Chargement en une requête (client, créateur, lignes, produits) pour éviter le N+1
        // au mapping des listes et lors des agrégats du tableau de bord.
        return orderRepository.findAllWithDetails(cashierScope.restrictedUserId());
    }

    /**
     * Page de commandes, filtrée et triée en base, chaque ligne entièrement chargée.
     *
     * <h2>Pourquoi deux requêtes</h2>
     *
     * Une seule ne suffit pas, et ce n'est pas un détail d'optimisation. La liste a besoin des
     * ARTICLES de chaque commande (le tableau en affiche le décompte, la recherche libre porte
     * sur leurs libellés) ; les charger demande un {@code JOIN FETCH} sur une collection. Or
     * une requête qui embarque un tel fetch ne peut pas être paginée par la base : la jointure
     * multiplie les lignes, un {@code LIMIT 50} couperait une commande au milieu de ses
     * articles. Hibernate le sait, rapatrie TOUT et pagine en mémoire — en le signalant par
     * l'avertissement {@code HHH90003004}. On aurait alors la même consommation qu'avant, plus
     * le coût de la découpe : une pagination purement décorative.
     *
     * Le premier temps sélectionne donc les commandes SANS leurs articles : pas de collection
     * jointe, donc un vrai {@code LIMIT/OFFSET} et un {@code COUNT} exact. Le second recharge
     * le détail des seules commandes retenues.
     *
     * <h2>Le montant filtré n'est pas une colonne</h2>
     *
     * L'écran affiche et filtre un montant TTC qui n'existe nulle part en base : facturée, la
     * commande vaut le total de sa facture (TVA et remise arrêtées à l'émission) ; pas encore
     * facturée, ce montant n'existe pas et l'écran l'estime au taux des réglages. La même règle
     * est reproduite ici en SQL, faute de quoi une fourchette exclurait des lignes dont le
     * chiffre affiché est pourtant dans l'intervalle — ce qui se lit comme une panne.
     */
    @Transactional(readOnly = true)
    public Page<Order> searchOrders(OrderSearchCriteria criteria, BigDecimal taxRate, Pageable pageable) {
        return searchOrders(criteria, taxRate, pageable, null);
    }

    /**
     * Variante triée sur le montant TTC affiché.
     *
     * Ce montant n'étant pas une colonne (voir la méthode ci-dessus), aucun {@code sort=} de
     * Spring Data ne peut le désigner : il faut poser l'ORDER BY sur l'expression calculée
     * elle-même. {@code payableDirection} porte le sens demandé ; le {@code pageable} reçu doit
     * alors être NON trié, sans quoi Spring Data écraserait cet ordre par le sien.
     *
     * Trier sur {@code finalAmount} à la place aurait été plus simple et faux : dès qu'une
     * facture existe, l'ordre obtenu contredirait les chiffres affichés dans la colonne.
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

        // Réordonnancement selon la page demandée : `IN (...)` ne garantit aucun ordre, et
        // laisser la base décider ferait sauter les lignes d'une page à l'autre au rafraîchissement.
        Map<Long, Order> byId = orderRepository.findAllWithDetailsByIds(ids).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        List<Order> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();

        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    /** Décompte par statut sur le périmètre de l'appelant — voir {@link OrderSummary}. */
    @Transactional(readOnly = true)
    public OrderSummary getSummary() {
        OrderRepository.OrderSummaryView v = orderRepository.summaryFor(
                cashierScope.restrictedUserId(),
                Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED, Order.OrderStatus.INVOICED,
                Order.OrderStatus.DELIVERED, Order.OrderStatus.CANCELED);
        return new OrderSummary(v.getTotal(), v.getPending(), v.getConfirmed(),
                v.getInvoiced(), v.getDelivered(), v.getCanceled());
    }

    /** Opérateurs et villes proposés par les filtres — voir {@link OrderFilterOptions}. */
    @Transactional(readOnly = true)
    public OrderFilterOptions getFilterOptions() {
        Long restrictedUserId = cashierScope.restrictedUserId();
        List<OrderFilterOptions.Operator> operators = orderRepository.findDistinctOperators(restrictedUserId)
                .stream()
                .map(v -> new OrderFilterOptions.Operator(v.getId(), operatorLabel(v)))
                .toList();
        return new OrderFilterOptions(operators, orderRepository.findDistinctClientCities(restrictedUserId));
    }

    /** « Prénom Nom », à défaut le nom de connexion — la règle qu'appliquait l'écran. */
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

            // Tri sur le montant TTC calculé. Posé ici parce que l'expression n'existe que dans
            // cette requête ; ignoré sur la requête de comptage, qui rend un Long et n'a que
            // faire d'un ORDER BY — PostgreSQL le refuserait d'ailleurs sur un COUNT.
            if (payableDirection != null && !isCountQuery(query)) {
                Expression<BigDecimal> payable = payableAmount(root, query, cb, taxRate);
                query.orderBy(payableDirection == Sort.Direction.ASC
                                ? cb.asc(payable) : cb.desc(payable),
                        // Départage stable : deux commandes du même montant garderaient sinon un
                        // ordre libre, et sauteraient d'une page à l'autre au rafraîchissement.
                        cb.desc(root.get("id")));
            }

            // Cloisonnement caissier, posé ici comme dans findAllWithDetails : il doit être
            // évalué EN BASE, sinon la page renvoyée serait celle de toute l'entreprise, filtrée
            // ensuite — un caissier verrait des pages à moitié vides et un total faux.
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
                // Borne haute exclusive au lendemain minuit : createdAt est un horodatage, une
                // commande passée à 14 h le jour de fin serait sinon écartée.
                predicates.add(cb.lessThan(root.get("createdAt"), c.dateTo().plusDays(1).atStartOfDay()));
            }
            if (c.onlyDiscounted()) {
                predicates.add(cb.greaterThan(cb.coalesce(root.get("discount"), BigDecimal.ZERO), BigDecimal.ZERO));
            }
            if (c.notes() != null && !c.notes().isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("notes"), "")),
                        "%" + c.notes().toLowerCase().trim() + "%"));
            }

            // Article ou famille commandés : sous-requête EXISTS plutôt qu'une jointure, qui
            // dupliquerait la commande autant de fois qu'elle a de lignes correspondantes et
            // fausserait le COUNT de la pagination.
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
     * « Pas encore facturée » est l'ABSENCE de facture vivante, pas un statut : une commande
     * dont la facture a été annulée y retombe, exactement comme le fait l'écran.
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
     * {@code EXISTS} d'une facture non annulée sur la commande, éventuellement d'un statut donné.
     * Une facture annulée ne compte jamais : elle est sortie des livres.
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
     * Fourchette sur le montant TTC affiché, reconstruit en SQL selon la règle de l'écran :
     * total de la facture vivante s'il en existe une, sinon net HT majoré du taux des réglages.
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
     * Montant TTC affiché, reconstruit en SQL : total de la facture vivante s'il en existe une,
     * sinon net HT majoré du taux des réglages.
     *
     * Une seule définition, partagée par la fourchette et par le tri. Deux copies auraient fini
     * par diverger, et la divergence se serait vue au pire endroit : une ligne triée à une place
     * que la fourchette exclut.
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

    /** La requête de comptage que Spring Data dérive de la spécification rend un {@code Long}. */
    private boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return Long.class.equals(type) || long.class.equals(type);
    }

    /**
     * Recherche libre. Un seul champ interroge le numéro, le client sous toutes ses formes,
     * l'opérateur, les notes et les articles commandés — c'est ce qui permet de retrouver une
     * vente à partir de ce dont on se souvient, sans savoir dans quel champ chercher.
     */
    private void addFullTextPredicate(OrderSearchCriteria c, Root<Order> root, CriteriaQuery<?> query,
                                      CriteriaBuilder cb, List<Predicate> predicates) {
        if (c.q() == null || c.q().isBlank()) {
            return;
        }
        String pattern = "%" + c.q().toLowerCase().trim() + "%";
        Path<Object> client = root.get("client");
        Path<Object> createdBy = root.get("createdBy");

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

    /** COALESCE avant LOWER : une colonne nulle ferait échouer la comparaison au lieu de la rendre fausse. */
    private Predicate like(CriteriaBuilder cb, Path<?> path, String pattern) {
        return cb.like(cb.lower(cb.coalesce(path.as(String.class), "")), pattern);
    }

    /**
     * Lecture unitaire. Hors périmètre, la commande est rendue absente plutôt que refusée :
     * un caissier ne doit pas pouvoir déduire, par la différence entre 403 et 404, l'existence
     * ni le volume des ventes de ses collègues.
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
     * Ventes d'un opérateur donné. L'accès à un autre opérateur que soi-même est barré en
     * amont, par le {@code @PreAuthorize} du contrôleur ; la garde est répétée ici pour que
     * la méthode reste sûre quel que soit son appelant.
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
     * Crée une commande à l'état brouillon (PENDING). Le stock n'est PAS touché ici : il ne
     * sera décrémenté qu'à la confirmation (confirmOrder). On fige les prix unitaires et on
     * calcule les totaux ; une vérification de disponibilité en lecture seule (sans verrou ni
     * décrément) rejette d'emblée un brouillon manifestement irréalisable, tout en laissant le
     * contrôle ferme et atomique à la confirmation.
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

            // Contrôle indicatif (non bloquant côté stock réel) : feedback immédiat. La sortie
            // de stock et le contrôle atomique sous verrou ont lieu à la confirmation.
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

        Order savedOrder = orderRepository.save(order);

        logActivity(ActivityLog.ActionType.SALE, "Order", savedOrder.getId(),
            "Création de la commande " + savedOrder.getOrderNumber() + " - Montant: " + savedOrder.getFinalAmount());

        return savedOrder;
    }

    /**
     * Confirme une commande (PENDING → CONFIRMED). C'est ici que le stock sort réellement :
     * chaque produit est verrouillé (findByIdForUpdate), son stock contrôlé puis décrémenté,
     * et un mouvement STOCK_OUT est journalisé pour la traçabilité. Échoue sans rien modifier
     * si le stock est insuffisant au moment de la confirmation.
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

        // Agréger les quantités par produit (une commande peut référencer 2× le même produit).
        java.util.Map<Long, Integer> quantityByProduct = new java.util.HashMap<>();
        for (OrderItem item : order.getItems()) {
            quantityByProduct.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }

        // Verrou pessimiste + contrôle ferme avant tout décrément : on ne sort le stock que si
        // toutes les lignes sont satisfaisables, pour éviter une confirmation partielle.
        for (java.util.Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("product", entry.getKey()));
            if (product.getStockQuantity() < entry.getValue()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), entry.getValue());
            }
            recordMovement(product, StockMovement.MovementType.STOCK_OUT, -entry.getValue(),
                    "Vente — commande " + order.getOrderNumber(), order.getOrderNumber());
        }

        return transitionTo(order, Order.OrderStatus.CONFIRMED);
    }

    /**
     * Modifie une commande encore au stade brouillon. L'édition des articles n'est autorisée
     * qu'en PENDING : une fois la commande confirmée, ses lignes sont figées (elles ont sorti
     * du stock et servent de base à la facture). Aucune opération de stock ici puisque PENDING
     * n'a jamais consommé de stock — on se contente de re-figer les prix et recalculer les totaux.
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

        // Mise à jour partielle : seuls les champs réellement envoyés sont écrasés. Un appelant
        // qui ne gère que les articles (l'écran Commandes, par exemple) ne doit pas remettre la
        // remise à zéro ni effacer les notes au passage. Une chaîne vide, elle, efface bien les
        // notes — c'est le seul moyen de les vider.
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
     * Changement de statut manuel. Seules les transitions sans document associé sont permises
     * ici : confirmation (→ CONFIRMED, qui sort le stock) et annulation (→ CANCELED). Les
     * passages à INVOICED / DELIVERED sont pilotés exclusivement par la facturation et la
     * livraison, afin de garantir l'invariant « INVOICED ⟺ une facture existe ».
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
     * Applique une transition de statut à une commande après validation de la machine à états.
     * Point d'entrée unique pour toute mutation de Order.status — à utiliser depuis les
     * services externes (InvoiceService, DeliveryService) plutôt que setStatus direct.
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

        // Cohérence financière : on n'annule pas une commande dont la facture est encore vivante,
        // ce qui laisserait une facture active rattachée à une commande annulée. La facture doit
        // d'abord être annulée (InvoiceService.cancelInvoice).
        invoiceRepository.findByOrderId(order.getId())
                .filter(inv -> inv.getStatus() != Invoice.InvoiceStatus.CANCELED)
                .ifPresent(inv -> {
                    throw BusinessException.of("order.cancel.invoiceAttached",
                            "Une facture est associée à cette commande — "
                                    + "annulez d'abord la facture " + inv.getInvoiceNumber(),
                            inv.getInvoiceNumber());
                });

        requireNoReturns(order, "order.cancel.returnAttached",
                "Un retour client a déjà été enregistré sur la commande " + order.getOrderNumber()
                        + " : l'annuler restituerait au stock des articles déjà rendus. "
                        + "Corrigez le stock par un ajustement.");

        // Restaurer le stock uniquement s'il avait été consommé (à la confirmation). Une commande
        // restée PENDING n'a jamais sorti de stock : rien à restaurer. Le retour est tracé (STOCK_IN).
        if (stockWasConsumed(order.getStatus())) {
            restoreStock(order, "Annulation — commande " + order.getOrderNumber());
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

        // Restaurer le stock seulement s'il avait été consommé et que la commande n'est pas
        // déjà annulée (auquel cas le stock a déjà été restitué lors de l'annulation).
        if (order.getStatus() != Order.OrderStatus.CANCELED && stockWasConsumed(order.getStatus())) {
            restoreStock(order, "Suppression — commande " + order.getOrderNumber());
        }

        String orderNumber = order.getOrderNumber();
        orderRepository.delete(order);

        logActivity(ActivityLog.ActionType.DELETE, "Order", id,
            "Suppression de la commande " + orderNumber);
    }

    /**
     * Refuse une annulation ou une suppression dès qu'un retour client porte sur la vente.
     *
     * {@link #restoreStock} restitue la quantité vendue entière : sur une commande dont une
     * partie a déjà été rendue, le stock serait crédité deux fois des mêmes articles. Le cas
     * n'est pas rattrapable en déduisant les quantités rendues — un échange à l'identique
     * réintègre puis ressort la marchandise, et ne doit donc rien retrancher. Un dossier qui a
     * connu un retour se solde par un ajustement de stock, tracé lui aussi dans le grand livre.
     *
     * Même garde-fou à la suppression : la ligne de retour référence la commande, la supprimer
     * ferait tomber la contrainte d'intégrité et laisserait un retour sans vente d'origine.
     */
    private void requireNoReturns(Order order, String messageKey, String message) {
        if (stockReturnRepository.countByOrderId(order.getId()) > 0) {
            throw BusinessException.of(messageKey, message, order.getOrderNumber());
        }
    }

    /** Vrai si le statut implique que le stock a déjà été décrémenté (depuis la confirmation). */
    private boolean stockWasConsumed(Order.OrderStatus status) {
        return status == Order.OrderStatus.CONFIRMED
                || status == Order.OrderStatus.INVOICED
                || status == Order.OrderStatus.DELIVERED;
    }

    /** Restitue au stock les quantités de chaque ligne, sous verrou pessimiste, en traçant un STOCK_IN. */
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
