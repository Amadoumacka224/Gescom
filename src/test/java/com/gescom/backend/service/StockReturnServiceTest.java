package com.gescom.backend.service;

import com.gescom.backend.dto.stock.ReturnLookupResponse;
import com.gescom.backend.dto.stock.StockReturnItemRequest;
import com.gescom.backend.dto.stock.StockReturnRequest;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.StockReturn;
import com.gescom.backend.entity.StockReturnItem;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.InsufficientStockException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles métier des retours clients : ce qui borne les quantités rendues et ce que chaque
 * traitement fait au stock.
 *
 * Test unitaire pur (Mockito) : ni base ni contexte Spring. Aucun utilisateur n'étant
 * authentifié, le log d'activité et l'auteur des mouvements restent silencieux.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockReturnServiceTest {

    @Mock private StockReturnRepository stockReturnRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;

    private StockReturnService service;

    private Product product;

    @BeforeEach
    void setUp() {
        service = new StockReturnService(stockReturnRepository, orderRepository, invoiceRepository,
                productRepository, stockMovementRepository, userRepository, activityLogService);

        product = new Product();
        product.setId(7L);
        product.setName("Clavier");
        product.setCode("P-007");
        product.setUnit("pièce");
        product.setStockQuantity(10);

        when(stockReturnRepository.save(any(StockReturn.class))).thenAnswer(invocation -> {
            StockReturn saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
                saved.setReturnNumber("RET-TEST");
            }
            return saved;
        });
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));
        when(invoiceRepository.findByOrderId(anyLong())).thenReturn(Optional.empty());
        when(stockReturnRepository.sumReturnedQuantitiesByOrder(anyLong())).thenReturn(List.of());
    }

    /** Commande facturée de 5 claviers à 12,00 € net l'unité (total de ligne 60,00 €). */
    private Order invoicedOrderOf5() {
        Order order = new Order();
        order.setId(3L);
        order.setOrderNumber("CMD-TEST");
        order.setStatus(Order.OrderStatus.INVOICED);
        order.setTotalAmount(new BigDecimal("60.00"));
        order.setFinalAmount(new BigDecimal("60.00"));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("12.00"));
        item.setTotalPrice(new BigDecimal("60.00"));
        item.setOrder(order);
        order.setItems(new java.util.ArrayList<>(List.of(item)));

        // L'enregistrement relit la commande sous verrou pessimiste, la recherche non : la
        // consultation n'a rien à sérialiser.
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
        when(orderRepository.findByOrderNumberWithDetails("CMD-TEST")).thenReturn(Optional.of(order));
        return order;
    }

    private StockReturnRequest requestOf(int quantity, StockReturnItem.ReturnTreatment treatment,
                                         Long replacementProductId) {
        return new StockReturnRequest(3L, List.of(new StockReturnItemRequest(
                7L, quantity, StockReturnItem.ReturnReason.DEFECTIVE, treatment, replacementProductId)), null);
    }

    /** Mouvements de stock écrits, dans l'ordre où le service les a produits. */
    private List<StockMovement> capturedMovements() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void lookup_findsSaleByOrderNumber_andExposesReturnableQuantities() {
        invoicedOrderOf5();

        ReturnLookupResponse response = service.lookup("CMD-TEST");

        assertThat(response.orderNumber()).isEqualTo("CMD-TEST");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantitySold()).isEqualTo(5);
        assertThat(response.items().get(0).quantityReturnable()).isEqualTo(5);
        // Prix unitaire net = total de ligne / quantité, pas le tarif courant du produit.
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("12.00");
    }

    @Test
    void lookup_deductsAlreadyReturnedQuantities() {
        invoicedOrderOf5();
        when(stockReturnRepository.sumReturnedQuantitiesByOrder(3L))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 2L }));

        ReturnLookupResponse response = service.lookup("CMD-TEST");

        assertThat(response.items().get(0).quantityReturned()).isEqualTo(2);
        assertThat(response.items().get(0).quantityReturnable()).isEqualTo(3);
    }

    @Test
    void lookup_rejectsOrderWhoseStockNeverLeft() {
        Order order = invoicedOrderOf5();
        order.setStatus(Order.OrderStatus.PENDING);

        assertThatThrownBy(() -> service.lookup("CMD-TEST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CMD-TEST");
    }

    @Test
    void restock_putsGoodsBackInStock_withoutRefund() {
        invoicedOrderOf5();

        StockReturn result = service.createReturn(requestOf(2, StockReturnItem.ReturnTreatment.RESTOCK, null));

        assertThat(product.getStockQuantity()).isEqualTo(12);
        assertThat(result.getTotalQuantity()).isEqualTo(2);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");

        List<StockMovement> movements = capturedMovements();
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo(StockMovement.MovementType.RETURN);
        assertThat(movements.get(0).getPreviousStock()).isEqualTo(10);
        assertThat(movements.get(0).getNewStock()).isEqualTo(12);
        // Le mouvement porte le numéro du retour : c'est le lien entre le grand livre et le document.
        assertThat(movements.get(0).getReference()).isEqualTo("RET-TEST");
    }

    /**
     * Remise globale de la vente : elle n'est pas portée par les lignes mais par la commande,
     * en montant. Le retournable doit être proratisé, sinon on rembourse plus que l'encaissé.
     */
    @Test
    void globalDiscount_isProratedOverTheReturnedLines() {
        Order order = invoicedOrderOf5();
        // 60,00 € de lignes, 6,00 € de remise globale : le client a payé 54,00 €, soit 10,80 €
        // le clavier au lieu de 12,00 €.
        order.setDiscount(new BigDecimal("6.00"));
        order.setFinalAmount(new BigDecimal("54.00"));

        ReturnLookupResponse response = service.lookup("CMD-TEST");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("10.80");

        StockReturn result = service.createReturn(requestOf(3, StockReturnItem.ReturnTreatment.REFUND, null));
        assertThat(result.getRefundAmount()).isEqualByComparingTo("32.40");
    }

    /**
     * Une fois la vente facturée, c'est la remise de la facture qui fait foi : elle cumule
     * celle de la commande et celle consentie à la facturation (cf. InvoiceService.createInvoice).
     */
    @Test
    void invoiceDiscount_prevailsOverTheOrderDiscount() {
        Order order = invoicedOrderOf5();
        order.setDiscount(new BigDecimal("6.00"));

        Invoice invoice = new Invoice();
        invoice.setId(11L);
        invoice.setInvoiceNumber("FACT-TEST");
        invoice.setSubtotal(new BigDecimal("60.00"));
        invoice.setDiscount(new BigDecimal("12.00")); // 6,00 € commande + 6,00 € facturation
        when(invoiceRepository.findByOrderId(3L)).thenReturn(Optional.of(invoice));

        // 48,00 € payés sur 60,00 € de lignes : 9,60 € le clavier.
        assertThat(service.lookup("CMD-TEST").items().get(0).unitPrice()).isEqualByComparingTo("9.60");

        StockReturn result = service.createReturn(requestOf(2, StockReturnItem.ReturnTreatment.REFUND, null));
        assertThat(result.getRefundAmount()).isEqualByComparingTo("19.20");
    }

    @Test
    void refund_putsGoodsBackInStock_andTracesAmountPaid() {
        invoicedOrderOf5();

        StockReturn result = service.createReturn(requestOf(3, StockReturnItem.ReturnTreatment.REFUND, null));

        assertThat(product.getStockQuantity()).isEqualTo(13);
        assertThat(result.getRefundAmount()).isEqualByComparingTo("36.00");
    }

    @Test
    void identicalExchange_leavesStockUnchanged_butTracesBothMovements() {
        invoicedOrderOf5();

        service.createReturn(requestOf(2, StockReturnItem.ReturnTreatment.EXCHANGE, null));

        // L'article rendu entre, son remplaçant identique ressort : solde net nul.
        assertThat(product.getStockQuantity()).isEqualTo(10);

        List<StockMovement> movements = capturedMovements();
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).getType()).isEqualTo(StockMovement.MovementType.RETURN);
        assertThat(movements.get(1).getType()).isEqualTo(StockMovement.MovementType.STOCK_OUT);
        assertThat(movements.get(1).getPreviousStock()).isEqualTo(12);
        assertThat(movements.get(1).getNewStock()).isEqualTo(10);
    }

    @Test
    void exchangeAgainstAnotherProduct_takesReplacementOutOfStock() {
        invoicedOrderOf5();
        Product replacement = new Product();
        replacement.setId(9L);
        replacement.setName("Souris");
        replacement.setStockQuantity(4);
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(replacement));

        service.createReturn(requestOf(2, StockReturnItem.ReturnTreatment.EXCHANGE, 9L));

        assertThat(product.getStockQuantity()).isEqualTo(12);
        assertThat(replacement.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void exchange_isRefusedWhenReplacementIsOutOfStock() {
        invoicedOrderOf5();
        Product replacement = new Product();
        replacement.setId(9L);
        replacement.setName("Souris");
        replacement.setStockQuantity(1);
        when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(replacement));

        assertThatThrownBy(() -> service.createReturn(
                requestOf(2, StockReturnItem.ReturnTreatment.EXCHANGE, 9L)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void quantityBeyondWhatWasSold_isRefusedWithoutTouchingStock() {
        invoicedOrderOf5();

        assertThatThrownBy(() -> service.createReturn(
                requestOf(6, StockReturnItem.ReturnTreatment.RESTOCK, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Clavier");

        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void previouslyReturnedQuantities_shrinkWhatCanStillBeReturned() {
        invoicedOrderOf5();
        when(stockReturnRepository.sumReturnedQuantitiesByOrder(3L))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 4L }));

        // 4 déjà rendus sur 5 vendus : la deuxième unité est de trop.
        assertThatThrownBy(() -> service.createReturn(
                requestOf(2, StockReturnItem.ReturnTreatment.RESTOCK, null)))
                .isInstanceOf(BusinessException.class);

        // Une seule reste possible.
        service.createReturn(requestOf(1, StockReturnItem.ReturnTreatment.RESTOCK, null));
        assertThat(product.getStockQuantity()).isEqualTo(11);
    }

    @Test
    void twoLinesOnTheSameProduct_areCheckedTogether() {
        invoicedOrderOf5();
        StockReturnRequest request = new StockReturnRequest(3L, List.of(
                new StockReturnItemRequest(7L, 3, StockReturnItem.ReturnReason.DEFECTIVE,
                        StockReturnItem.ReturnTreatment.RESTOCK, null),
                new StockReturnItemRequest(7L, 3, StockReturnItem.ReturnReason.NOT_SATISFIED,
                        StockReturnItem.ReturnTreatment.REFUND, null)), null);

        // Chaque ligne tient dans les 5 vendus, mais leur somme (6) non.
        assertThatThrownBy(() -> service.createReturn(request))
                .isInstanceOf(BusinessException.class);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    /**
     * Le contrôle « vendu − déjà rendu » porte sur le cumul des retours de la vente : verrouiller
     * les produits ne suffit pas, deux retours simultanés liraient le même cumul avant de le
     * faire. La commande doit donc être relue sous verrou, pas par un findById ordinaire.
     */
    @Test
    void createReturn_readsTheSaleUnderPessimisticLock() {
        invoicedOrderOf5();

        service.createReturn(requestOf(1, StockReturnItem.ReturnTreatment.RESTOCK, null));

        verify(orderRepository).findByIdForUpdate(3L);
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void productAbsentFromTheSale_isRefused() {
        invoicedOrderOf5();
        StockReturnRequest request = new StockReturnRequest(3L, List.of(
                new StockReturnItemRequest(99L, 1, StockReturnItem.ReturnReason.OTHER,
                        StockReturnItem.ReturnTreatment.RESTOCK, null)), null);

        assertThatThrownBy(() -> service.createReturn(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CMD-TEST");
    }

    @Test
    void canceledOrder_cannotBeReturned() {
        Order order = invoicedOrderOf5();
        order.setStatus(Order.OrderStatus.CANCELED);

        assertThatThrownBy(() -> service.createReturn(
                requestOf(1, StockReturnItem.ReturnTreatment.RESTOCK, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("annulée");
    }
}
