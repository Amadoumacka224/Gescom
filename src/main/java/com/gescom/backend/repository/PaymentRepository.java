package com.gescom.backend.repository;

import com.gescom.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIntentId(String intentId);

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    /**
     * Sessions de terminal encore ouvertes sur une facture. Sert à garantir l'invariant
     * « une seule session confirmable à la fois » : ouvrir une nouvelle session referme
     * celles-ci (cf. PaymentService.createIntent).
     */
    List<Payment> findByInvoiceIdAndStatus(Long invoiceId, Payment.PaymentStatus status);
}
