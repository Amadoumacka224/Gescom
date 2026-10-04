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
 * Retours clients. Comme pour les mouvements de stock, on ne fait qu'en ajouter, donc les
 * listes sont paginées.
 *
 * La liste ne charge pas les lignes de retour exprès : un {@code JOIN FETCH} sur une
 * collection oblige Hibernate à paginer en mémoire. L'en-tête suffit pour le tableau ; le
 * détail est chargé avec {@link #findDetailedById(Long)} quand on ouvre un retour.
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
     * Quantités déjà rendues sur une vente, par produit : {@code [productId, quantité]}.
     * C'est la protection principale : ce qu'on peut encore retourner sur une ligne, c'est
     * ce qui a été vendu moins ce total, donc on ne peut pas rendre deux fois le même article.
     */
    @Query("SELECT i.product.id, SUM(i.quantity) FROM StockReturnItem i "
            + "WHERE i.stockReturn.order.id = :orderId GROUP BY i.product.id")
    List<Object[]> sumReturnedQuantitiesByOrder(@Param("orderId") Long orderId);

    long countByOrderId(Long orderId);

    /**
     * Plus grand numero deja utilise pour ce prefixe et cette annee, ou null s'il n'y en a pas.
     *
     * Un MAX sur la CHAINE, ce qui marche parce que le compteur a toujours la meme longueur
     * (complete avec des zeros) : RET-2026-0042 est bien avant RET-2026-0100. Sans les zeros,
     * l'ordre alphabetique mettrait 9 apres 10 et la numerotation repartirait en arriere.
     *
     * Les anciens numeros (RET- suivi d'un horodatage) ne correspondent pas au motif : ils
     * sont ignores, et les deux formats peuvent exister ensemble sans probleme.
     *
     * Le filtrage par entreprise est fait par le filtre Hibernate, qui s'applique aux
     * requetes JPQL : chaque entreprise ne voit que ses propres numeros.
     */
    @Query("SELECT MAX(r.returnNumber) FROM StockReturn r WHERE r.returnNumber LIKE :pattern")
    String findMaxNumber(@Param("pattern") String pattern);
}
