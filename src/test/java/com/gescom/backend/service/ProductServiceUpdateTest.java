package com.gescom.backend.service;

import com.gescom.backend.entity.Product;
import com.gescom.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Propriété du stock : la fiche produit ne le réécrit pas.
 *
 * Le formulaire d'édition part de la fiche telle qu'elle était à son ouverture. Si la mise à jour
 * recopiait {@code stockQuantity}, toute correction de prix ou de libellé ramènerait le stock à sa
 * valeur d'alors — effaçant en silence les ventes de l'intervalle, sans mouvement pour l'expliquer.
 * Le stock ne bouge que par une opération tracée (vente, retour, ajustement).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceUpdateTest {

    @Mock private ProductRepository productRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private StockService stockService;

    private ProductService service;

    private Product stored;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, activityLogService, stockService);

        // Fiche en base : 170 en stock, après une vente de 10 sur les 180 d'origine.
        stored = new Product();
        stored.setId(4L);
        stored.setName("Clavier");
        stored.setSellingPrice(new BigDecimal("25.00"));
        stored.setStockQuantity(170);
        stored.setMinStockAlert(5);

        when(productRepository.findById(4L)).thenReturn(Optional.of(stored));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_ignoresTheStockCarriedByTheRequest() {
        // Le formulaire avait été ouvert avant la vente : il repose encore 180.
        Product details = new Product();
        details.setName("Clavier mécanique");
        details.setSellingPrice(new BigDecimal("29.00"));
        details.setStockQuantity(180);
        details.setMinStockAlert(8);

        Product updated = service.updateProduct(4L, details);

        assertThat(updated.getStockQuantity()).isEqualTo(170);
        assertThat(updated.getName()).isEqualTo("Clavier mécanique");
        assertThat(updated.getSellingPrice()).isEqualByComparingTo("29.00");
        assertThat(updated.getMinStockAlert()).isEqualTo(8);
    }

    @Test
    void update_withoutStockInTheRequest_leavesTheStockIntact() {
        // Client à jour : il n'envoie plus le stock du tout. La colonne est NOT NULL — recopier
        // ce null ferait échouer l'enregistrement.
        Product details = new Product();
        details.setName("Clavier");
        details.setSellingPrice(new BigDecimal("25.00"));
        details.setMinStockAlert(5);

        Product updated = service.updateProduct(4L, details);

        assertThat(updated.getStockQuantity()).isEqualTo(170);
    }

    @Test
    void update_neverWritesAStockMovement() {
        Product details = new Product();
        details.setName("Clavier");
        details.setSellingPrice(new BigDecimal("25.00"));
        details.setStockQuantity(999);
        details.setMinStockAlert(5);

        service.updateProduct(4L, details);

        // Un stock qui bouge sans mouvement rendrait le grand livre irréconciliable :
        // la fiche produit ne peut donc pas être un chemin d'écriture du stock.
        verifyNoInteractions(stockService);
    }

    @Test
    void updateStock_stillGoesThroughTheTracedPath() {
        // Le chemin légitime d'un ajustement : il passe par StockService, qui journalise
        // le mouvement correspondant.
        service.updateStock(4L, -3);

        verify(stockService).removeStock(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void updateStock_withZeroDelta_doesNothing() {
        service.updateStock(4L, 0);

        verify(stockService, never()).removeStock(anyLong(), anyInt(), any(), any(), any());
        verify(stockService, never()).addStock(anyLong(), anyInt(), any(), any(), any(), any());
    }
}
