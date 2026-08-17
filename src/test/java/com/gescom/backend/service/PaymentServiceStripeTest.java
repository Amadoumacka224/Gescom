package com.gescom.backend.service;

import com.gescom.backend.security.CashierScope;
import com.gescom.backend.config.StripeProperties;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Payment;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.service.stripe.SimulatedStripeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Parcours complet du terminal de paiement, joué sur la passerelle simulée : création de
 * l'intention, confirmation, puis mise à jour de la facture.
 *
 * La passerelle et {@link InvoiceService} sont ici de vraies instances — c'est justement
 * l'enchaînement des trois étapes que l'on veut vérifier, pas les appels que PaymentService
 * prétend faire. Seuls les dépôts sont simulés (aucune base, aucun contexte Spring).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceStripeTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private OrderService orderService;

    private PaymentService paymentService;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        InvoiceService invoiceService = new InvoiceService(
                invoiceRepository, orderRepository, activityLogService, orderService, new CashierScope());

        StripeProperties properties = new StripeProperties();
        properties.setMode("simulated");
        properties.setCurrency("eur");

        paymentService = new PaymentService(paymentRepository, invoiceRepository, invoiceService,
                activityLogService, new SimulatedStripeGateway(), properties, new CashierScope());

        invoice = unpaidInvoiceOf100();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // Le dépôt des paiements se comporte comme une base : un identifiant à l'insertion,
        // et la même instance retrouvée ensuite par findById.
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
            return payment;
        });
        when(paymentRepository.findByInvoiceIdAndStatus(anyLong(), any()))
                .thenReturn(List.of());
    }

    /** Facture de 100,00 € encore due, rattachée à une commande facturée. */
    private Invoice unpaidInvoiceOf100() {
        Order order = new Order();
        order.setId(5L);
        order.setOrderNumber("CMD-TEST");
        order.setStatus(Order.OrderStatus.INVOICED);

        Invoice inv = new Invoice();
        inv.setId(1L);
        inv.setInvoiceNumber("FACT-TEST");
        inv.setOrder(order);
        inv.setTotalAmount(new BigDecimal("100.00"));
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setRemainingAmount(new BigDecimal("100.00"));
        inv.setStatus(Invoice.InvoiceStatus.UNPAID);
        return inv;
    }

    @Test
    void createIntent_withoutAmount_opensSessionForTheRemainingBalance() {
        Payment payment = paymentService.createIntent(1L, null);

        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REQUIRES_CONFIRMATION);
        assertThat(payment.getAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getCurrency()).isEqualTo("EUR");
        assertThat(payment.getIntentId()).startsWith("pi_sim_");
        assertThat(payment.isSimulated()).isTrue();
        // Le secret accompagne la réponse d'ouverture, sans être persisté (champ @Transient).
        assertThat(payment.getClientSecret()).contains("_secret_");
        // Rien n'est encaissé tant que la carte n'a pas été présentée.
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.UNPAID);
    }

    @Test
    void confirm_withAcceptedCard_settlesInvoiceAndRecordsCardPayment() {
        Payment created = paymentService.createIntent(1L, null);

        Payment confirmed = paymentService.confirmIntent(created.getId(), "pm_card_visa");

        assertThat(confirmed.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCEEDED);
        assertThat(confirmed.getCardBrand()).isEqualTo("VISA");
        assertThat(confirmed.getCardLast4()).isEqualTo("4242");
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getFailureMessage()).isNull();

        // La facture est soldée par le même chemin qu'un règlement en espèces.
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.getPaymentMethod()).isEqualTo(Invoice.PaymentMethod.CREDIT_CARD);
        assertThat(invoice.getPaymentDate()).isNotNull();
        // La commande, elle, reste INVOICED : son statut ne dépend pas du règlement.
        assertThat(invoice.getOrder().getStatus()).isEqualTo(Order.OrderStatus.INVOICED);
    }

    @Test
    void confirm_withPartialAmount_leavesInvoicePartiallyPaid() {
        Payment created = paymentService.createIntent(1L, new BigDecimal("40.00"));

        paymentService.confirmIntent(created.getId(), "pm_card_visa");

        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void confirm_withDeclinedCard_recordsFailureAndLeavesInvoiceUntouched() {
        Payment created = paymentService.createIntent(1L, null);

        Payment refused = paymentService.confirmIntent(created.getId(), "pm_card_chargeDeclined");

        assertThat(refused.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(refused.getFailureMessage()).contains("refusée");
        assertThat(refused.getCardLast4()).isEqualTo("0002");
        // Un refus ne doit rien encaisser.
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.UNPAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void confirm_withInsufficientFundsCard_reportsTheDeclineReason() {
        Payment created = paymentService.createIntent(1L, null);

        Payment refused = paymentService.confirmIntent(
                created.getId(), "pm_card_chargeDeclinedInsufficientFunds");

        assertThat(refused.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(refused.getFailureMessage()).contains("Provision insuffisante");
    }

    @Test
    void confirm_twice_isRejected() {
        Payment created = paymentService.createIntent(1L, null);
        paymentService.confirmIntent(created.getId(), "pm_card_visa");

        assertThatThrownBy(() -> paymentService.confirmIntent(created.getId(), "pm_card_visa"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("plus en attente de confirmation");
    }

    /**
     * Une session restée ouverte (terminal fermé en route) ne doit pas condamner la facture :
     * la nouvelle session la referme. Sans quoi seule la plus récente est confirmable, ce qui
     * est bien l'invariant que l'on protège contre le double encaissement.
     */
    @Test
    void createIntent_whileAnotherSessionIsPending_closesTheStaleOne() {
        Payment stale = paymentService.createIntent(1L, null);
        when(paymentRepository.findByInvoiceIdAndStatus(1L, Payment.PaymentStatus.REQUIRES_CONFIRMATION))
                .thenReturn(List.of(stale));

        Payment fresh = paymentService.createIntent(1L, null);

        assertThat(stale.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELED);
        assertThat(fresh.getStatus()).isEqualTo(Payment.PaymentStatus.REQUIRES_CONFIRMATION);
        assertThat(fresh.getIntentId()).isNotEqualTo(stale.getIntentId());
    }

    @Test
    void createIntent_onPaidInvoice_isRejected() {
        invoice.setPaidAmount(new BigDecimal("100.00"));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);

        assertThatThrownBy(() -> paymentService.createIntent(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà payée");
    }

    @Test
    void createIntent_onCanceledInvoice_isRejected() {
        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);

        assertThatThrownBy(() -> paymentService.createIntent(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("annulée");
    }

    @Test
    void createIntent_withAmountAboveRemaining_isRejected() {
        assertThatThrownBy(() -> paymentService.createIntent(1L, new BigDecimal("150.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse le reste à payer");
    }

    @Test
    void confirm_afterInvoiceWasSettledElsewhere_cancelsSessionInsteadOfChargingTwice() {
        Payment created = paymentService.createIntent(1L, null);

        // La facture est soldée en espèces pendant que le client cherche sa carte.
        invoice.setPaidAmount(new BigDecimal("100.00"));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);

        // L'issue est retournée et non levée : une exception annulerait la transaction, donc le
        // passage à CANCELED, et la session resterait ouverte au lieu d'être close.
        Payment stale = paymentService.confirmIntent(created.getId(), "pm_card_visa");

        assertThat(stale.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELED);
        assertThat(stale.getFailureMessage()).contains("a changé depuis l'ouverture du paiement");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void cancelIntent_closesTheSession() {
        Payment created = paymentService.createIntent(1L, null);

        Payment canceled = paymentService.cancelIntent(created.getId());

        assertThat(canceled.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELED);
        assertThat(invoice.getStatus()).isEqualTo(Invoice.InvoiceStatus.UNPAID);
    }

    @Test
    void confirm_withUnknownTestCard_isRejected() {
        Payment created = paymentService.createIntent(1L, null);

        assertThatThrownBy(() -> paymentService.confirmIntent(created.getId(), "pm_card_inconnue"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Moyen de paiement de test inconnu");
    }
}
