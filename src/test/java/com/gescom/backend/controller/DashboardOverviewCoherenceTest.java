package com.gescom.backend.controller;

import com.gescom.backend.dto.dashboard.DashboardOverview;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.UserRepository;
import com.gescom.backend.service.DashboardService;
import com.gescom.backend.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence des chiffres du tableau de bord.
 *
 * <h2>Pourquoi ce test a changé de nature</h2>
 *
 * Il vérifiait les mêmes invariants sur un contrôleur nourri de listes simulées, à l'époque où
 * les agrégats se calculaient en mémoire. Ils sont désormais produits par des requêtes
 * d'agrégation : l'arithmétique est dans la base, et un mock de repository ne peut plus rien en
 * dire — il rendrait exactement les chiffres qu'on lui aurait dictés.
 *
 * Le test s'exécute donc contre une vraie base (H2 en mémoire, schéma dérivé des entités, voir
 * le commentaire de la dépendance H2 dans le pom). C'est plus fort qu'avant : ce sont les
 * requêtes SQL réelles qui sont mises à l'épreuve, pas la façon dont on les a simulées.
 *
 * <h2>Ce qui est vérifié</h2>
 *
 * Des IDENTITÉS, pas des valeurs. L'écran affiche ces chiffres côte à côte comme des
 * répartitions : si les parts ne totalisent pas le tout, le tableau de bord se contredit
 * lui-même sous les yeux de son lecteur, et aucune ligne n'est pourtant fausse.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "platform.admin.username=",
        "platform.admin.email=",
        "platform.admin.password=",
        "jwt.secret=test-secret-suffisamment-long-pour-hmac-sha256-aaaaaaaaaaaaaaaa",
})
@Transactional
class DashboardOverviewCoherenceTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;

    private Client client;
    private User operator;

    /**
     * Un jeu couvrant chaque cas que les invariants doivent absorber : les cinq statuts de
     * commande, une commande SANS montant, les quatre statuts de facture dont une annulée, et
     * une livraison sur une commande facturée.
     */
    @BeforeEach
    void setUp() {
        TenantContext.clear();
        deliveryRepository.deleteAll();
        invoiceRepository.deleteAll();
        orderRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setName("Test");
        company.setSlug("test-dashboard");
        company.setEmail("test@example.test");
        company.setCountry("Belgique");
        company = companyRepository.save(company);

        // Contexte posé comme le ferait JwtAuthenticationFilter : les entités sont estampillées
        // par TenantEntityListener, et les agrégats s'exécutent sous le filtre de cloisonnement
        // — exactement les conditions de production.
        TenantContext.setCompanyId(company.getId());

        // Une vente porte toujours son opérateur : createdBy est obligatoire en base, et c'est
        // aussi lui qui porterait le cloisonnement caissier si l'aperçu n'était pas réservé aux
        // ADMIN.
        User operator = new User();
        operator.setUsername("operateur-test");
        operator.setEmail("operateur@example.test");
        operator.setPassword("x");
        operator.setFirstName("Operateur");
        operator.setLastName("Test");
        operator.setRole(User.Role.ADMIN);
        this.operator = userRepository.save(operator);

        client = clientRepository.save(client("Anne", "Test"));

        Order pending = order(Order.OrderStatus.PENDING, new BigDecimal("100.00"));
        Order confirmed = order(Order.OrderStatus.CONFIRMED, new BigDecimal("200.00"));
        Order invoiced = order(Order.OrderStatus.INVOICED, new BigDecimal("300.00"));
        Order delivered = order(Order.OrderStatus.DELIVERED, new BigDecimal("400.00"));
        // Annulée : elle ne doit compter ni dans le chiffre d'affaires ni parmi les honorées.
        order(Order.OrderStatus.CANCELED, new BigDecimal("999.00"));

        invoice(invoiced, Invoice.InvoiceStatus.UNPAID, new BigDecimal("300.00"), BigDecimal.ZERO);
        invoice(delivered, Invoice.InvoiceStatus.PAID, new BigDecimal("400.00"), new BigDecimal("400.00"));
        invoice(confirmed, Invoice.InvoiceStatus.PARTIALLY_PAID, new BigDecimal("200.00"), new BigDecimal("50.00"));
        // Annulée : sortie des livres, elle ne doit peser sur aucun des trois montants.
        invoice(pending, Invoice.InvoiceStatus.CANCELED, new BigDecimal("100.00"), new BigDecimal("10.00"));

        delivery(delivered, Delivery.DeliveryStatus.DELIVERED);
    }

    /** Un contexte laissé en place contaminerait le test suivant — les threads sont réutilisés. */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Les cinq decomptes de commandes totalisent le nombre de commandes")
    void orderStatusCountsAreExhaustive() {
        DashboardOverview o = dashboardService.getOverview();

        assertThat(o.pendingOrders() + o.confirmedOrders() + o.invoicedOrders()
                + o.deliveredOrders() + o.canceledOrders())
                .as("les parts doivent totaliser le tout : l'ecran en fait une repartition")
                .isEqualTo(o.totalOrders());
    }

    @Test
    @DisplayName("Le chiffre d'affaires exclut les commandes annulees")
    void salesExcludeCanceledOrders() {
        DashboardOverview o = dashboardService.getOverview();

        // 100 + 200 + 300 + 400, sans les 999 de l'annulee ni la commande sans montant.
        assertThat(o.totalSales()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    /**
     * Le calcul en mémoire filtrait les montants nuls, un {@code null} hérité ayant fait tomber
     * l'aperçu en 500. Écrire ce cas contre une vraie base a montré qu'il ne peut pas exister :
     * {@code final_amount} est NOT NULL DEFAULT 0 depuis V6_1. La garde protégeait d'un état que
     * le schéma interdit.
     *
     * Reste le cas réel, que ce test couvre : un catalogue de commandes VIDE. SUM y rend NULL et
     * non 0, d'où le COALESCE de {@code sumHonoredSales}.
     */
    @Test
    @DisplayName("Sans aucune commande, le chiffre d'affaires vaut zero et non nul")
    void salesOnEmptySetIsZeroNotNull() {
        deliveryRepository.deleteAll();
        invoiceRepository.deleteAll();
        orderRepository.deleteAll();

        assertThat(dashboardService.getOverview().totalSales())
                .isNotNull()
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Les quatre decomptes de factures totalisent le nombre de factures")
    void invoiceStatusCountsAreExhaustive() {
        DashboardOverview o = dashboardService.getOverview();

        assertThat(o.unpaidInvoices() + o.partiallyPaidInvoices() + o.paidInvoices()
                + o.canceledInvoices())
                .isEqualTo(o.totalInvoices());
    }

    @Test
    @DisplayName("Facture = encaisse + reliquat, sur les seules factures vivantes")
    void invoiceAmountsDescribeTheSameSet() {
        DashboardOverview o = dashboardService.getOverview();

        assertThat(o.invoicedAmount())
                .as("l'identite doit tomber juste a l'euro pres : les trois montants sont affiches cote a cote")
                .isEqualByComparingTo(o.totalRevenue().add(o.pendingAmount()));

        // Encaissé : 400 (soldée) + 50 (partielle). Les 10 € de la facture ANNULÉE sont exclus —
        // compter son encaissement sans compter son reliquat donnerait un taux d'encaissement
        // qui n'est celui d'aucun périmètre.
        assertThat(o.totalRevenue()).isEqualByComparingTo(new BigDecimal("450.00"));
        // Facturé vivant : 300 + 400 + 200, sans les 100 de l'annulée.
        assertThat(o.invoicedAmount()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @DisplayName("Le reste a planifier compte les commandes facturees sans livraison")
    void ordersToScheduleCountsInvoicedOrdersWithoutDelivery() {
        DashboardOverview o = dashboardService.getOverview();

        // Une seule commande est INVOICED, et elle n'a pas de livraison. La commande DELIVERED
        // en a une mais n'est plus au stade facturé.
        //
        // Compté sur la jointure réelle, et non déduit de « facturées − livraisons en attente » :
        // une livraison peut survivre à l'annulation de sa facture, auquel cas la soustraction
        // sous-compte, voire passe sous zéro.
        assertThat(o.ordersToSchedule()).isEqualTo(1);
    }

    @Test
    @DisplayName("Les decomptes de livraisons totalisent le nombre de livraisons")
    void deliveryStatusCountsAreExhaustive() {
        DashboardOverview o = dashboardService.getOverview();

        assertThat(o.pendingDeliveries() + o.deliveredDeliveries())
                .isEqualTo(o.totalDeliveries());
    }

    // ── Fabriques ────────────────────────────────────────────────────────────

    private Client client(String firstName, String lastName) {
        Client c = new Client();
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setPhone("0470000000");
        return c;
    }

    /**
     * Numéro posé explicitement, et ce n'est pas de la coquetterie de test.
     *
     * {@code Order.@PrePersist} le compose sinon avec {@code System.currentTimeMillis()}. Six
     * commandes créées d'affilée tiennent dans la même milliseconde : la contrainte
     * {@code uq_orders_company_number} saute, et le test échoue sur un doublon de numéro.
     *
     * C'est la collision annoncée comme théorique dans l'audit, observée ici pour de vrai. La
     * numérotation par horodatage reste à remplacer par une séquence par entreprise ; en
     * attendant, ce compteur évite au test de dépendre de la vitesse de la machine.
     */
    private int orderSequence;

    private Order order(Order.OrderStatus status, BigDecimal finalAmount) {
        Order o = new Order();
        o.setOrderNumber(String.format("CMD-TEST-%03d", ++orderSequence));
        o.setClient(client);
        o.setCreatedBy(operator);
        o.setStatus(status);
        o.setTotalAmount(finalAmount != null ? finalAmount : BigDecimal.ZERO);
        o.setDiscount(BigDecimal.ZERO);
        o.setFinalAmount(finalAmount);
        return orderRepository.save(o);
    }

    private int invoiceSequence;

    private void invoice(Order order, Invoice.InvoiceStatus status, BigDecimal total, BigDecimal paid) {
        Invoice i = new Invoice();
        // Même raison que pour les commandes : FACT- + horodatage collisionne en rafale.
        i.setInvoiceNumber(String.format("FACT-TEST-%03d", ++invoiceSequence));
        i.setOrder(order);
        i.setStatus(status);
        i.setInvoiceDate(LocalDate.now());
        i.setDueDate(LocalDate.now().plusDays(30));
        i.setSubtotal(total);
        i.setTotalAmount(total);
        i.setPaidAmount(paid);
        i.setPaymentMethod(Invoice.PaymentMethod.CASH);
        invoiceRepository.save(i);
    }

    private void delivery(Order order, Delivery.DeliveryStatus status) {
        Delivery d = new Delivery();
        d.setOrder(order);
        d.setStatus(status);
        d.setDeliveryAddress("Rue de Test 1");
        d.setScheduledDate(LocalDateTime.now());
        deliveryRepository.save(d);
    }
}
