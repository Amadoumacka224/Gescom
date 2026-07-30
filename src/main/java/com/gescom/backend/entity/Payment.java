package com.gescom.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trace d'une transaction carte passée par le terminal de paiement (Stripe, mode test).
 *
 * Le parcours Stripe se déroule en deux temps — création de l'intention puis confirmation —
 * séparés par un aller-retour côté terminal. Cette entité est ce qui relie les deux : elle
 * mémorise l'identifiant d'intention renvoyé par Stripe ({@code pi_...}), le montant engagé
 * et la facture visée, pour que la confirmation retrouve son contexte sans faire confiance
 * au client.
 *
 * Elle ne remplace pas l'encaissement : quand la transaction aboutit, c'est
 * {@code InvoiceService.recordPayment} qui met la facture à jour. Ce que l'on garde ici,
 * c'est le journal de ce qu'a répondu le prestataire, échecs compris.
 *
 * Aucun secret n'est persisté : le {@code client_secret} n'existe que dans la réponse HTTP
 * de création, le temps de la session du terminal.
 */
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Prestataire ; une seule valeur aujourd'hui, le champ documente la provenance de l'id. */
    @Column(nullable = false, length = 20)
    private String provider = "STRIPE";

    /** Identifiant d'intention de paiement Stripe ({@code pi_...}). */
    @Column(nullable = false, unique = true, length = 100)
    private String intentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.REQUIRES_CONFIRMATION;

    @Column(length = 30)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    /** Motif de refus tel que renvoyé par Stripe, conservé pour le journal et l'affichage. */
    @Column(length = 255)
    private String failureMessage;

    /** Vrai si la transaction a été jouée par la passerelle simulée (aucun appel à Stripe). */
    @Column(nullable = false)
    private boolean simulated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime confirmedAt;

    /**
     * Secret d'usage unique renvoyé par Stripe à la création. Explicitement @Transient : il
     * n'accompagne la transaction que le temps de la réponse HTTP qui ouvre la session du
     * terminal, et n'a aucune raison de survivre en base.
     */
    @Transient
    private String clientSecret;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Cycle de vie d'une transaction, restreint à ce que le terminal sait traiter.
     *
     * Les états intermédiaires de Stripe ({@code requires_action}, {@code processing}) ne sont
     * pas modélisés : un terminal encaisse ou refuse. Une intention qui réclamerait une
     * authentification 3-D Secure est donc classée FAILED, avec le motif renvoyé par Stripe.
     */
    public enum PaymentStatus {
        REQUIRES_CONFIRMATION,  // Intention créée, en attente de confirmation par le terminal
        SUCCEEDED,              // Paiement accepté — la facture a été encaissée
        FAILED,                 // Refusé par l'émetteur (ou non traitable par le terminal)
        CANCELED                // Session abandonnée depuis le terminal
    }
}
