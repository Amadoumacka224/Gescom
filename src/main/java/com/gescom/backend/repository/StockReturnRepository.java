package com.gescom.backend.repository;

import com.gescom.backend.entity.StockReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Registre des retours clients. Comme le grand livre des mouvements, il ne fait que croître :
 * ses listes sont donc paginées.
 *
 * La page de liste ne charge délibérément pas les lignes de retour — un {@code JOIN FETCH} sur
 * une collection force Hibernate à paginer en mémoire. L'entête suffit au tableau ; le détail
 * est chargé ligne par ligne via {@link #findDetailedById(Long)} à l'ouverture d'un retour.
 */
@Repository
public interface StockReturnRepository extends JpaRepository<StockReturn, Long>,
        JpaSpecificationExecutor<StockReturn> {

    @Override
    @EntityGraph(attributePaths = {"order", "order.client", "invoice", "createdBy"})
    Page<StockReturn> findAll(Specification<StockReturn> spec, Pageable pageable);

    @Query("SELECT DISTINCT r FROM StockReturn r "
            + "LEFT JOIN FETCH r.order o "
            + "LEFT JOIN FETCH o.client "
            + "LEFT JOIN FETCH r.invoice "
            + "LEFT JOIN FETCH r.createdBy "
            + "LEFT JOIN FETCH r.items i "
            + "LEFT JOIN FETCH i.product "
            + "LEFT JOIN FETCH i.replacementProduct "
            + "WHERE r.id = :id")
    Optional<StockReturn> findDetailedById(@Param("id") Long id);

    /**
     * Quantités déjà rendues sur une vente, ventilées par produit : {@code [productId, quantité]}.
     * C'est le garde-fou du module — la quantité retournable d'une ligne est ce qui a été vendu
     * moins ce total, ce qui interdit de rendre deux fois le même article.
     */
    @Query("SELECT i.product.id, SUM(i.quantity) FROM StockReturnItem i "
            + "WHERE i.stockReturn.order.id = :orderId GROUP BY i.product.id")
    List<Object[]> sumReturnedQuantitiesByOrder(@Param("orderId") Long orderId);

    long countByOrderId(Long orderId);

    /**
     * Plus haut numero deja attribue pour ce prefixe et cette annee, ou null s'il n'y en a
     * aucun.
     *
     * Un MAX sur la CHAINE, valide parce que le compteur est complete a largeur fixe :
     * RET-2026-0042 se compare bien avant RET-2026-0100. Sans ce remplissage, l'ordre
     * lexicographique placerait 9 apres 10 et la suite repartirait en arriere.
     *
     * Les numeros de l'ancien format (RET- suivi d'un horodatage) ne matchent pas le motif :
     * ils sont ignores, et les deux formes cohabitent sans se marcher dessus.
     *
     * Le cloisonnement par entreprise est assure par le filtre Hibernate, qui couvre les
     * requetes JPQL : chaque entreprise ne voit donc que ses propres numeros.
     */
    @Query("SELECT MAX(r.returnNumber) FROM StockReturn r WHERE r.returnNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
