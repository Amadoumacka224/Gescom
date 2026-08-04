package com.gescom.backend.service;

import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.StockReturnRepository;
import com.gescom.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
 * Annulation et suppression d'une commande sur laquelle un retour client a été enregistré.
 *
 * {@code restoreStock} restitue la quantité vendue entière : sur une vente partiellement rendue,
 * elle recréditerait le stock d'articles déjà réintégrés par le retour. Ces deux opérations sont
 * donc refusées dès qu'un retour existe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceCancellationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private StockReturnRepository stockReturnRepository;
    @Mock private ActivityLogService activityLogService;

    private OrderService service;

    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, productRepository, userRepository,
                stockMovementRepository, invoiceRepository, stockReturnRepository, activityLogService);

        product = new Product();
        product.setId(7L);
        product.setName("Clavier");
        product.setStockQuantity(10);

        // Commande confirmée de 5 claviers : son stock est sorti, une annulation le restituerait.
        order = new Order();
        order.setId(3L);
        order.setOrderNumber("CMD-TEST");
        order.setStatus(Order.OrderStatus.CONFIRMED);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("12.00"));
        item.setTotalPrice(new BigDecimal("60.00"));
        item.setOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));
        when(invoiceRepository.findByOrderId(3L)).thenReturn(Optional.empty());
        when(stockReturnRepository.countByOrderId(3L)).thenReturn(0L);
    }

    @Test
    void cancel_withoutReturns_restoresTheWholeSoldQuantity() {
        service.cancelOrder(3L);

        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELED);
        assertThat(product.getStockQuantity()).isEqualTo(15);
    }

    @Test
    void cancel_isRefusedWhenASaleAlreadyHadAReturn() {
        when(stockReturnRepository.countByOrderId(3L)).thenReturn(1L);

        assertThatThrownBy(() -> service.cancelOrder(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CMD-TEST");

        // Ni statut ni stock touchés : les articles rendus l'ont déjà été par le retour.
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void delete_isRefusedWhenASaleAlreadyHadAReturn() {
        when(stockReturnRepository.countByOrderId(3L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteOrder(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CMD-TEST");

        verify(orderRepository, never()).delete(any(Order.class));
        assertThat(product.getStockQuantity()).isEqualTo(10);
    }
}
