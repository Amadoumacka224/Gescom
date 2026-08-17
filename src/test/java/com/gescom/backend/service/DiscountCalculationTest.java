package com.gescom.backend.service;

import com.gescom.backend.security.CashierScope;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Vérification du calcul des remises, de la ligne de commande jusqu'au total de la facture.
 * Test unitaire pur (Mockito) : aucune base ni contexte Spring.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscountCalculationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private StockReturnRepository stockReturnRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ActivityLogService activityLogService;

    @Mock private DocumentNumberService documentNumberService;

    private OrderService orderService;
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository, userRepository,
                stockMovementRepository, invoiceRepository, stockReturnRepository, activityLogService,
                new CashierScope(), documentNumberService);
        invoiceService = new InvoiceService(invoiceRepository, orderRepository, activityLogService, orderService,
                new CashierScope(), paymentRepository, deliveryRepository, documentNumberService);

        User caissier = new User();
        caissier.setId(1L);
        caissier.setUsername("caissier");
        when(userRepository.findByUsername("caissier")).thenReturn(Optional.of(caissier));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("caissier", null, List.of()));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByOrderId(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Product product(long id, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Produit " + id);
        p.setSellingPrice(new BigDecimal(price));
        p.setStockQuantity(stock);
        p.setCategory(new Category());
        when(productRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private OrderItem line(Product product, int qty, String discountPercent) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(qty);
        item.setDiscount(new BigDecimal(discountPercent));
        return item;
    }

    private Order draft(List<OrderItem> items) {
        Order order = new Order();
        order.getItems().addAll(items);
        return order;
    }

    private Invoice invoiceFor(Order order, String taxRate, BigDecimal discount) {
        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setTaxRate(new BigDecimal(taxRate));
        invoice.setDiscount(discount);
        invoice.setPaidAmount(BigDecimal.ZERO);
        return invoice;
    }

    // ── Remise de ligne (en %) ────────────────────────────────────────────────────────

    @Test
    void remiseDeLigne_estAppliqueeEtArrondieA2Decimales() {
        Product a = product(1L, "10.00", 100);
        Product b = product(2L, "19.99", 100);

        Order order = orderService.createOrder(draft(List.of(
                line(a, 3, "10"),      // 30.00 − 10 %  = 27.00
                line(b, 2, "33.33")))); // 39.98 − 33,33 % = 26.654666 → 26.65

        assertThat(order.getItems().get(0).getTotalPrice()).isEqualByComparingTo("27.00");
        assertThat(order.getItems().get(1).getTotalPrice()).isEqualByComparingTo("26.65");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("53.65");
    }

    @Test
    void remiseDeLigne_100PourCent_donneUneLigneGratuite() {
        Product a = product(1L, "42.50", 10);
        Order order = orderService.createOrder(draft(List.of(line(a, 2, "100"))));
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void remiseDeLigne_horsBornes_estRamèneeDansIntervalle() {
        Product a = product(1L, "10.00", 100);
        Product b = product(2L, "10.00", 100);

        Order negative = orderService.createOrder(draft(List.of(line(a, 1, "-50"))));
        assertThat(negative.getTotalAmount()).isEqualByComparingTo("10.00");

        Order over = orderService.createOrder(draft(List.of(line(b, 1, "150"))));
        assertThat(over.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    // ── Remise globale de commande (en €) ─────────────────────────────────────────────

    @Test
    void remiseGlobaleDeCommande_estDeduiteDuMontantFinal() {
        Product a = product(1L, "10.00", 100);
        Order draft = draft(List.of(line(a, 5, "0")));
        draft.setDiscount(new BigDecimal("5.00"));

        Order order = orderService.createOrder(draft);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(order.getFinalAmount()).isEqualByComparingTo("45.00");
    }

    @Test
    void remiseGlobaleDeCommande_superieureAuTotal_estRefusee() {
        Product a = product(1L, "10.00", 100);
        Order draft = draft(List.of(line(a, 5, "0")));
        draft.setDiscount(new BigDecimal("80.00"));

        assertThatThrownBy(() -> orderService.createOrder(draft))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse le total de la commande");
    }

    @Test
    void remiseGlobaleDeCommande_estRepriseParLaFacturation() {
        Product a = product(1L, "10.00", 100);
        Order draft = draft(List.of(line(a, 5, "0")));
        draft.setDiscount(new BigDecimal("5.00"));
        Order order = orderService.createOrder(draft);
        order.setId(7L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        Invoice result = invoiceService.createInvoice(invoiceFor(order, "21", BigDecimal.ZERO));

        // Sous-total 50,00 € − remise de commande 5,00 € = 45,00 €, soit le finalAmount.
        assertThat(result.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(result.getDiscount()).isEqualByComparingTo("5.00");
        assertThat(result.getSubtotal().subtract(result.getDiscount()))
                .isEqualByComparingTo(order.getFinalAmount());
        assertThat(result.getTaxAmount()).isEqualByComparingTo("9.45"); // 45 × 21 %
        assertThat(result.getTotalAmount()).isEqualByComparingTo("54.45");
    }

    @Test
    void remisesDeCommandeEtDeFacture_seCumulent() {
        Product a = product(1L, "10.00", 100);
        Order draft = draft(List.of(line(a, 5, "0")));
        draft.setDiscount(new BigDecimal("5.00"));
        Order order = orderService.createOrder(draft);
        order.setId(7L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        Invoice result = invoiceService.createInvoice(invoiceFor(order, "0", new BigDecimal("10.00")));

        assertThat(result.getDiscount()).isEqualByComparingTo("15.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("35.00");
    }

    // ── Remise commerciale de facture (en €) ──────────────────────────────────────────

    @Test
    void remiseDeFacture_estDeduiteAvantLaTva() {
        Product a = product(1L, "10.00", 100);
        Order order = orderService.createOrder(draft(List.of(line(a, 6, "10")))); // 54.00
        order.setId(7L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        Invoice result = invoiceService.createInvoice(invoiceFor(order, "21", new BigDecimal("4.00")));

        assertThat(result.getSubtotal()).isEqualByComparingTo("54.00");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("10.50"); // (54 − 4) × 21 %
        assertThat(result.getTotalAmount()).isEqualByComparingTo("60.50");
        assertThat(result.getRemainingAmount()).isEqualByComparingTo("60.50");
    }

    @Test
    void remiseDeFacture_superieureAuSousTotal_estRefusee() {
        Product a = product(1L, "10.00", 100);
        Order order = orderService.createOrder(draft(List.of(line(a, 5, "0")))); // 50.00
        order.setId(7L);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> invoiceService.createInvoice(invoiceFor(order, "21", new BigDecimal("80.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse le sous-total");

        // La commande n'a pas été facturée : elle reste facturable.
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    // ── Mise à jour partielle d'une commande ──────────────────────────────────────────

    @Test
    void miseAJourSansRemiseNiNotes_neLesEffacePas() {
        Product a = product(1L, "10.00", 100);
        Order existing = orderService.createOrder(draft(List.of(line(a, 5, "0"))));
        existing.setId(7L);
        existing.setDiscount(new BigDecimal("5.00"));
        existing.setFinalAmount(new BigDecimal("45.00"));
        existing.setNotes("Livraison en main propre");
        when(orderRepository.findById(7L)).thenReturn(Optional.of(existing));

        // Patch tel que l'envoie l'écran Commandes : les articles, rien d'autre.
        Order patch = draft(List.of(line(a, 6, "0")));
        patch.setDiscount(null);
        patch.setNotes(null);

        Order result = orderService.updateOrder(7L, patch);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(result.getDiscount()).isEqualByComparingTo("5.00");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("55.00");
        assertThat(result.getNotes()).isEqualTo("Livraison en main propre");
    }

    @Test
    void miseAJourAvecNotesVides_lesEfface() {
        Product a = product(1L, "10.00", 100);
        Order existing = orderService.createOrder(draft(List.of(line(a, 5, "0"))));
        existing.setId(7L);
        existing.setNotes("À supprimer");
        when(orderRepository.findById(7L)).thenReturn(Optional.of(existing));

        Order patch = draft(List.of(line(a, 5, "0")));
        patch.setDiscount(null);
        patch.setNotes("");

        assertThat(orderService.updateOrder(7L, patch).getNotes()).isNull();
    }
}
