package com.gescom.backend.service;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Payment;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.PaymentRepository;
import com.gescom.backend.security.CashierScope;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conditions pour supprimer une facture.
 *
 * Avant, la suppression effaçait la ligne sans rien vérifier : une facture payée, avec des
 * paiements carte ou dont la commande était déjà livrée était supprimée sans rien dire.
 * Maintenant ces cas sont refusés, et le seul cas autorisé (une facture annulée sans rien de
 * lié) l'est toujours.
 *
 * Test unitaire simple (Mockito) : pas de base ni de contexte Spring.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDeletionGuardTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private OrderService orderService;

    @Mock private DocumentNumberService documentNumberService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, orderRepository, activityLogService,
                orderService, new CashierScope(), paymentRepository, deliveryRepository, documentNumberService);
    }

    /**
     * Facture annulée, sans paiement, sans paiement carte, sans livraison : le seul cas qu'on
     * peut supprimer. Chaque test change UNE seule de ces conditions, pour que si un test
     * échoue, on sache tout de suite quelle vérification pose problème.
     */
    private Invoice deletableInvoice() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNumber("CMD-TEST");

        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setInvoiceNumber("FACT-TEST");
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setStatus(Invoice.InvoiceStatus.CANCELED);
        invoice.setOrder(order);

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(deliveryRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        return invoice;
    }

    @Test
    void supprime_une_facture_annulee_sans_rien_de_rattache() {
        deletableInvoice();

        invoiceService.deleteInvoice(1L);

        verify(invoiceRepository).delete(any(Invoice.class));
    }

    @Test
    void refuse_de_supprimer_une_facture_encaissee() {
        Invoice invoice = deletableInvoice();
        // Payée en partie PUIS annulée : cancelInvoice ne refuse que les factures payées en
        // entier, donc ce cas peut arriver, et il y a de l'argent vraiment reçu.
        invoice.setPaidAmount(new BigDecimal("40.00"));

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FACT-TEST")
                .hasMessageContaining("40.00");

        verify(invoiceRepository, never()).delete(any(Invoice.class));
    }

    @Test
    void refuse_de_supprimer_une_facture_portant_un_paiement_carte() {
        deletableInvoice();
        // Intention créée puis jamais confirmée : aucun euro payé, mais une ligne existe et la
        // clé étrangère ferait échouer la suppression avec une 409 technique.
        Payment pending = new Payment();
        pending.setId(5L);
        pending.setStatus(Payment.PaymentStatus.REQUIRES_CONFIRMATION);
        when(paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FACT-TEST");

        verify(invoiceRepository, never()).delete(any(Invoice.class));
    }

    @Test
    void refuse_de_supprimer_une_facture_dont_la_commande_est_livree() {
        deletableInvoice();
        when(deliveryRepository.findByOrderId(10L)).thenReturn(Optional.of(new Delivery()));

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CMD-TEST");

        verify(invoiceRepository, never()).delete(any(Invoice.class));
    }

    @Test
    void refuse_de_supprimer_une_facture_qui_n_a_pas_ete_annulee() {
        Invoice invoice = deletableInvoice();
        invoice.setStatus(Invoice.InvoiceStatus.UNPAID);

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FACT-TEST");

        verify(invoiceRepository, never()).delete(any(Invoice.class));
    }

    /**
     * Le paiement passe avant le statut : une facture payée et pas annulée doit être refusée
     * pour la raison la plus grave des deux, celle qui concerne l'argent.
     */
    @Test
    void signale_l_encaissement_avant_le_defaut_d_annulation() {
        Invoice invoice = deletableInvoice();
        invoice.setPaidAmount(new BigDecimal("100.00"));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100.00");
    }

    /** La vérification ne doit pas dépendre d'un paidAmount rempli. */
    @Test
    void tolere_un_montant_encaisse_absent() {
        Invoice invoice = deletableInvoice();
        invoice.setPaidAmount(null);

        invoiceService.deleteInvoice(1L);

        verify(invoiceRepository).delete(any(Invoice.class));
    }

    @Test
    void expose_la_cle_de_message_attendue_pour_chaque_refus() {
        Invoice invoice = deletableInvoice();
        invoice.setStatus(Invoice.InvoiceStatus.UNPAID);

        assertThatThrownBy(() -> invoiceService.deleteInvoice(1L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getMessageKey()).isEqualTo("invoice.delete.notCanceled"));
    }
}
