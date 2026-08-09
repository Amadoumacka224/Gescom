package com.gescom.backend.repository;

import com.gescom.backend.entity.SaasPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registre des encaissements d'abonnement — la source des revenus affiches au proprietaire.
 *
 * Les echecs y sont conserves au meme titre que les succes : c'est ce qui rend le taux de
 * reussite calculable. Toutes les sommes ne retiennent donc que le statut SUCCEEDED, et
 * jamais l'ensemble des lignes.
 */
@Repository
public interface SaasPaymentRepository extends JpaRepository<SaasPayment, Long> {

    Page<SaasPayment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SaasPayment> findByStatusOrderByCreatedAtDesc(SaasPayment.SaasPaymentStatus status, Pageable pageable);

    Page<SaasPayment> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<SaasPayment> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(SaasPayment.SaasPaymentStatus status);

    long countByStatusAndCreatedAtBetween(SaasPayment.SaasPaymentStatus status,
                                          LocalDateTime start, LocalDateTime end);

    boolean existsByReference(String reference);

    /** Revenu encaisse sur une periode. Seuls les paiements aboutis comptent. */
    @Query("""
           SELECT COALESCE(SUM(p.amount), 0) FROM SaasPayment p
           WHERE p.status = :status
             AND p.paidAt BETWEEN :start AND :end
           """)
    BigDecimal sumAmountByStatusAndPaidAtBetween(SaasPayment.SaasPaymentStatus status,
                                                 LocalDateTime start, LocalDateTime end);

    /** Revenu total encaisse depuis l'origine. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SaasPayment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(SaasPayment.SaasPaymentStatus status);

    /**
     * Revenu encaisse mois par mois, pour la courbe du tableau de bord.
     * L'agregation est faite en base : rapatrier le journal complet pour le regrouper en
     * memoire ne tiendrait pas a l'echelle de plusieurs annees d'exploitation.
     */
    @Query("""
           SELECT YEAR(p.paidAt), MONTH(p.paidAt), COALESCE(SUM(p.amount), 0), COUNT(p)
           FROM SaasPayment p
           WHERE p.status = :status AND p.paidAt >= :since
           GROUP BY YEAR(p.paidAt), MONTH(p.paidAt)
           ORDER BY YEAR(p.paidAt), MONTH(p.paidAt)
           """)
    List<Object[]> monthlyRevenueSince(SaasPayment.SaasPaymentStatus status, LocalDateTime since);

    /** Derniers echecs — bloc d'alertes du tableau de bord. */
    List<SaasPayment> findTop5ByStatusOrderByCreatedAtDesc(SaasPayment.SaasPaymentStatus status);
}
