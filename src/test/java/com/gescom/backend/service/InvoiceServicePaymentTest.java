package com.gescom.backend.service;

import com.gescom.backend.security.CashierScope;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Vérifie les règles métier de l'encaissement (paiement partiel et total) d'une facture.
 * Test unitaire pur (Mockito) : aucune base ni contexte Spring requis. Le log d'activité est
 * silencieux ici car aucun utilisateur n'est authentifié (getCurrentUserId() renvoie null).
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServicePaymentTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private OrderService orderService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, orderRepository, activityLogService, orderService,
                new CashierScope(), paymentRepository, deliveryRepository);
    }

    /** Facture de référence : total 100,00 €, encore due (statut UNPAID). */
    private Invoice unpaidInvoiceOf100() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setInvoiceNumber("FACT-TEST");
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setRemainingAmount(new BigDecimal("100.00"));
        invoice.setStatus(Invoice.InvoiceStatus.UNPAID);
        return invoice;
    }

    private void givenInvoice(Invoice invoice) {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void partialPayment_setsStatusPartiallyPaid_andAccumulatesPaidAmount() {
        Invoice invoice = unpaidInvoiceOf100();
        givenInvoice(invoice);

        Invoice result = invoiceService.recordPayment(
                1L, new BigDecimal("40.00"), Invoice.PaymentMethod.CASH, LocalDate.now());

        assertThat(result.getStatus()).isEqualTo(Invoice.InvoiceStatus.PARTIALLY_PAID);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("40.00");
        // La facture n'est pas soldée : pas de date de règlement complet.
        assertThat(result.getPaymentDate()).isNull();
    }

    @Test
    void successivePartialPayments_settleInvoice_andMarkItPaid() {
        Invoice invoice = unpaidInvoiceOf100();
        givenInvoice(invoice);

        // Premier acompte.
        invoiceService.recordPayment(1L, new BigDecimal("40.00"), Invoice.PaymentMethod.CASH, LocalDate.now());
        // Solde du reste dû.
        Invoice result = invoiceService.recordPayment(
                1L, new BigDecimal("60.00"), Invoice.PaymentMethod.CASH, LocalDate.now());

        assertThat(result.getStatus()).isEqualTo(Invoice.InvoiceStatus.PAID);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getPaymentDate()).isNotNull();
    }

    @Test
    void payment_exceedingRemaining_isRejected() {
        Invoice invoice = unpaidInvoiceOf100();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.recordPayment(
                1L, new BigDecimal("150.00"), Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse le reste à payer");
    }

    @Test
    void secondPayment_exceedingRemainingAfterPartial_isRejected() {
        Invoice invoice = unpaidInvoiceOf100();
        givenInvoice(invoice);

        invoiceService.recordPayment(1L, new BigDecimal("70.00"), Invoice.PaymentMethod.CASH, LocalDate.now());

        // Reste dû = 30,00 € : un paiement de 50,00 € doit être refusé (pas d'écrêtage silencieux).
        assertThatThrownBy(() -> invoiceService.recordPayment(
                1L, new BigDecimal("50.00"), Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse le reste à payer");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void payment_onCanceledInvoice_isRejected() {
        Invoice invoice = unpaidInvoiceOf100();
        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.recordPayment(
                1L, new BigDecimal("10.00"), Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("annulée");
    }

    @Test
    void payment_onAlreadyPaidInvoice_isRejected() {
        Invoice invoice = unpaidInvoiceOf100();
        invoice.setPaidAmount(new BigDecimal("100.00"));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.recordPayment(
                1L, new BigDecimal("10.00"), Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà payée");
    }

    @Test
    void payment_withNonPositiveAmount_isRejected() {
        Invoice invoice = unpaidInvoiceOf100();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.recordPayment(
                1L, BigDecimal.ZERO, Invoice.PaymentMethod.CASH, LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("positif");
    }
}
