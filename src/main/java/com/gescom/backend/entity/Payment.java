package com.gescom.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historique d'une transaction carte passée par le terminal de paiement (Stripe, mode test).
 *
 * Avec Stripe, ça se passe en deux étapes (création de l'intention puis confirmation), avec
 * un aller-retour par le terminal entre les deux. Cette entité fait le lien : elle garde
 * l'id d'intention renvoyé par Stripe ({@code pi_...}), le montant et la facture, pour que
 * la confirmation retrouve tout sans faire confiance au client.
 *
 * Ce n'est pas elle qui enregistre le paiement : quand la transaction réussit, c'est
 * {@code InvoiceService.recordPayment} qui met la facture à jour. Ici on garde juste ce que
 * Stripe a répondu, y compris les échecs.
 *
 * Aucun secret n'est enregistré : le {@code client_secret} n'existe que dans la réponse HTTP
 * de création, pendant la session du terminal.
 */
@Entity
@Table(name = "payments")
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise a qui appartient la ligne, c'est ce qui separe les donnees des entreprises.
     *
     * Remplie automatiquement a la creation par TenantEntityListener : ni les services ni
     * les mappers n'ont a s'en occuper, donc pas de risque d'oubli. Pas envoyee en JSON
     * (les controleurs ne renvoient que des DTO).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company ownerCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Prestataire ; une seule valeur pour l'instant, ça indique d'où vient l'id. */
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

    /** Raison du refus renvoyée par Stripe, gardée pour l'historique et l'affichage. */
    @Column(length = 255)
    private String failureMessage;

    /** Vrai si la transaction est passée par la passerelle simulée (pas d'appel à Stripe). */
    @Column(nullable = false)
    private boolean simulated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime confirmedAt;

    /**
     * Secret à usage unique renvoyé par Stripe à la création. En @Transient exprès : il ne sert
     * que dans la réponse HTTP qui ouvre la session du terminal, pas besoin de le garder en base.
     */
    @Transient
    private String clientSecret;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * États d'une transaction, limités à ce que le terminal sait gérer.
     *
     * On ne gère pas les états intermédiaires de Stripe ({@code requires_action},
     * {@code processing}) : un terminal accepte ou refuse. Une intention qui demanderait une
     * validation 3-D Secure est donc mise en FAILED, avec la raison donnée par Stripe.
     */
    public enum PaymentStatus {
        REQUIRES_CONFIRMATION,  // Intention créée, en attente de confirmation par le terminal
        SUCCEEDED,              // Paiement accepté - la facture a été encaissée
        FAILED,                 // Refusé par l'émetteur (ou non traitable par le terminal)
        CANCELED                // Session abandonnée depuis le terminal
    }
}
