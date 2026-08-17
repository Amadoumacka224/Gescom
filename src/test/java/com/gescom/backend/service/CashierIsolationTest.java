package com.gescom.backend.service;

import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import com.gescom.backend.security.CashierScope;
import com.gescom.backend.security.OwnershipViolationException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cloisonnement d'un caissier à ses propres ventes.
 *
 * Trois comportements y sont vérifiés, parce que ce sont les trois façons dont un caissier
 * pourrait atteindre le dossier d'un collègue en appelant l'API directement, sans passer par
 * l'interface qui ne le lui propose jamais :
 * <ul>
 *   <li>les LISTES sont filtrées en base sur l'identifiant du caissier — et pas seulement à
 *       l'affichage ;</li>
 *   <li>les LECTURES unitaires d'une vente d'autrui se comportent comme une ressource absente,
 *       pour ne rien révéler de son existence ;</li>
 *   <li>les ÉCRITURES — modification, confirmation, annulation, encaissement — sont refusées
 *       franchement, y compris à travers les documents dérivés de la vente (facture, livraison,
 *       paiement carte).</li>
 * </ul>
 *
 * Le pendant de chacun est vérifié pour l'ADMIN, qui doit conserver la vue d'ensemble : un
 * cloisonnement qui aveuglerait aussi le responsable serait une régression, pas une sécurité.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashierIsolationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private StockReturnRepository stockReturnRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ActivityLogService activityLogService;

    private static final long ALICE_ID = 11L;
    private static final long BOB_ID = 22L;

    @Mock private DocumentNumberService documentNumberService;

    private OrderService orderService;
    private InvoiceService invoiceService;
    private DeliveryService deliveryService;

    /** Vente d'Alice — celle que Bob ne doit ni voir ni toucher. */
    private Order aliceOrder;
    private Invoice aliceInvoice;

    @BeforeEach
    void setUp() {
        CashierScope cashierScope = new CashierScope();
        orderService = new OrderService(orderRepository, productRepository, userRepository,
                stockMovementRepository, invoiceRepository, stockReturnRepository, activityLogService,
                cashierScope, documentNumberService);
        invoiceService = new InvoiceService(invoiceRepository, orderRepository, activityLogService,
                orderService, cashierScope, paymentRepository, deliveryRepository, documentNumberService);
        deliveryService = new DeliveryService(deliveryRepository, orderRepository, invoiceRepository,
                orderService, activityLogService, cashierScope, documentNumberService);

        aliceOrder = new Order();
        aliceOrder.setId(500L);
        aliceOrder.setOrderNumber("CMD-500");
        aliceOrder.setStatus(Order.OrderStatus.PENDING);
        aliceOrder.setTotalAmount(new BigDecimal("100.00"));
        aliceOrder.setFinalAmount(new BigDecimal("100.00"));
        aliceOrder.setItems(new ArrayList<>());
        aliceOrder.setCreatedBy(cashier(ALICE_ID, "alice"));

        aliceInvoice = new Invoice();
        aliceInvoice.setId(900L);
        aliceInvoice.setInvoiceNumber("FACT-900");
        aliceInvoice.setOrder(aliceOrder);
        aliceInvoice.setStatus(Invoice.InvoiceStatus.UNPAID);
        aliceInvoice.setTotalAmount(new BigDecimal("100.00"));
        aliceInvoice.setPaidAmount(BigDecimal.ZERO);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(aliceOrder));
        when(orderRepository.findByOrderNumber("CMD-500")).thenReturn(Optional.of(aliceOrder));
        when(invoiceRepository.findById(900L)).thenReturn(Optional.of(aliceInvoice));
        when(invoiceRepository.findByOrderId(500L)).thenReturn(Optional.of(aliceInvoice));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User cashier(long id, String username) {
        return userWithRole(id, username, User.Role.CAISSIER);
    }

    private User userWithRole(long id, String username, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    /**
     * Authentifie l'utilisateur donné comme le fait {@code JwtAuthenticationFilter} : le
     * principal est l'entité chargée en base, jamais un identifiant fourni par l'appelant.
     */
    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void loginAsBob() {
        authenticate(cashier(BOB_ID, "bob"));
    }

    private void loginAsAdmin() {
        authenticate(userWithRole(99L, "patron", User.Role.ADMIN));
    }

    // --- Listes : le filtre est appliqué en base ------------------------------

    @Test
    void listeDesCommandes_pourUnCaissier_estFiltreeSurSesPropresVentes() {
        loginAsBob();

        orderService.getAllOrders();

        // C'est l'identifiant de Bob qui part à la base : le filtrage n'est pas fait après coup,
        // donc rien de ce qui appartient à Alice n'est même chargé en mémoire.
        verify(orderRepository).findAllWithDetails(BOB_ID);
    }

    @Test
    void listeDesCommandes_pourUnAdmin_resteGlobale() {
        loginAsAdmin();

        orderService.getAllOrders();

        // null = aucune restriction : le responsable garde la vue sur toutes les caisses.
        verify(orderRepository).findAllWithDetails(null);
    }

    @Test
    void listeDesFactures_etDesLivraisons_suiventLeMemePerimetre() {
        loginAsBob();

        invoiceService.getAllInvoices();
        deliveryService.getAllDeliveries();

        verify(invoiceRepository).findAllWithDetails(BOB_ID);
        verify(deliveryRepository).findAllWithDetails(BOB_ID);
    }

    // --- Lecture unitaire : la vente d'autrui est rendue absente --------------

    @Test
    void lectureDeLaVenteDunCollegue_seComporteCommeUneVenteInexistante() {
        loginAsBob();

        // Absent plutôt que refusé : le contrôleur en tire un 404, indiscernable d'un
        // identifiant qui n'existe pas.
        assertThat(orderService.getOrderById(500L)).isEmpty();
        assertThat(orderService.getOrderByOrderNumber("CMD-500")).isEmpty();
        assertThat(invoiceService.getInvoiceById(900L)).isEmpty();
        assertThat(invoiceService.getInvoiceByOrder(500L)).isEmpty();
    }

    @Test
    void lectureDeSaPropreVente_resteAccessible() {
        authenticate(cashier(ALICE_ID, "alice"));

        assertThat(orderService.getOrderById(500L)).contains(aliceOrder);
        assertThat(invoiceService.getInvoiceById(900L)).contains(aliceInvoice);
    }

    @Test
    void lectureParUnAdmin_resteAccessible() {
        loginAsAdmin();

        assertThat(orderService.getOrderById(500L)).contains(aliceOrder);
        assertThat(invoiceService.getInvoiceById(900L)).contains(aliceInvoice);
    }

    @Test
    void listeDesVentesDunAutreOperateur_estRefusee() {
        loginAsBob();

        // La route la plus directe pour lire la caisse d'un collègue : demander son id.
        assertThatThrownBy(() -> orderService.getOrdersByUser(ALICE_ID))
                .isInstanceOf(OwnershipViolationException.class);

        assertThat(orderService.getOrdersByUser(BOB_ID)).isNotNull();
        verify(orderRepository).findByCreatedById(BOB_ID);
        verify(orderRepository, never()).findByCreatedById(ALICE_ID);
    }

    // --- Écriture : refus franc, et aucun effet de bord -----------------------

    @Test
    void modificationDeLaVenteDunCollegue_estRefusee() {
        loginAsBob();

        Order patch = new Order();
        patch.setItems(new ArrayList<>());

        assertThatThrownBy(() -> orderService.updateOrder(500L, patch))
                .isInstanceOf(OwnershipViolationException.class);

        // Le refus tombe avant toute écriture : la commande d'Alice ressort intacte.
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void confirmationDeLaVenteDunCollegue_estRefusee_etNeTouchePasAuStock() {
        loginAsBob();

        assertThatThrownBy(() -> orderService.confirmOrder(500L))
                .isInstanceOf(OwnershipViolationException.class);

        // Confirmer sort le stock : le refus doit précéder le moindre mouvement.
        verify(productRepository, never()).findByIdForUpdate(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void annulationDeLaVenteDunCollegue_estRefusee() {
        loginAsBob();

        assertThatThrownBy(() -> orderService.cancelOrder(500L))
                .isInstanceOf(OwnershipViolationException.class);

        assertThat(aliceOrder.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void suppressionDeLaVenteDunCollegue_estRefusee() {
        loginAsBob();

        assertThatThrownBy(() -> orderService.deleteOrder(500L))
                .isInstanceOf(OwnershipViolationException.class);

        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void encaissementSurLaFactureDunCollegue_estRefuse() {
        loginAsBob();

        assertThatThrownBy(() -> invoiceService.recordPayment(
                900L, new BigDecimal("50.00"), Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(OwnershipViolationException.class);

        // L'encaissement d'Alice ne doit pas être crédité à la caisse de Bob.
        assertThat(aliceInvoice.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void annulationDeLaFactureDunCollegue_estRefusee() {
        loginAsBob();

        assertThatThrownBy(() -> invoiceService.cancelInvoice(900L))
                .isInstanceOf(OwnershipViolationException.class);

        assertThat(aliceInvoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.UNPAID);
    }

    @Test
    void facturationDeLaVenteDunCollegue_estRefusee() {
        loginAsBob();

        Invoice draft = new Invoice();
        draft.setOrder(aliceOrder);

        assertThatThrownBy(() -> invoiceService.createInvoice(draft))
                .isInstanceOf(OwnershipViolationException.class);

        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void ecritureParUnAdmin_resteAutorisee() {
        loginAsAdmin();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockReturnRepository.countByOrderId(500L)).thenReturn(0L);
        when(invoiceRepository.findByOrderId(500L)).thenReturn(Optional.empty());

        orderService.cancelOrder(500L);

        // Le responsable conserve la main sur les dossiers de toutes ses caisses.
        assertThat(aliceOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELED);
    }
}
