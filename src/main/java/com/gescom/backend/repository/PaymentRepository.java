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
     * Sessions de terminal encore ouvertes sur une facture. Sert à garantir qu'il n'y a
     * qu'une seule session à confirmer à la fois : en ouvrir une nouvelle ferme celles-ci
     * (voir PaymentService.createIntent).
     */
    List<Payment> findByInvoiceIdAndStatus(Long invoiceId, Payment.PaymentStatus status);
}
