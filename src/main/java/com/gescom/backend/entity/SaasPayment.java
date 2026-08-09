package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Encaissement d'abonnement — le registre de revenus de la plateforme.
 *
 * A ne pas confondre avec {@link Payment}, qui trace les transactions carte du terminal
 * de caisse d'une entreprise : la, une entreprise encaisse ses propres clients ; ici,
 * c'est GESCOM qui encaisse ses entreprises abonnees.
 *
 * Les echecs sont conserves au meme titre que les succes — c'est ce qui rend le taux de
 * reussite et le suivi des impayes calculables. Le montant reste donc renseigne meme sur
 * un paiement FAILED, et seuls les SUCCEEDED alimentent le revenu encaisse.
 */
@Entity
@Table(name = "saas_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaasPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Abonnement couvert. Nullable : un versement exceptionnel (regularisation, prestation
     * ponctuelle) peut exister sans se rattacher a un contrat.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.0", message = "Le montant ne peut pas etre negatif")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaasPaymentStatus status = SaasPaymentStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method = PaymentMethod.TRANSFER;

    /** Periode d'abonnement couverte : distingue deux versements d'un meme mois. */
    @Column
    private LocalDateTime periodStart;

    @Column
    private LocalDateTime periodEnd;

    /** Date d'encaissement effectif ; renseignee uniquement quand le statut est SUCCEEDED. */
    @Column
    private LocalDateTime paidAt;

    @Size(max = 255)
    @Column(length = 255)
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SaasPaymentStatus {
        PENDING,    // Emis, en attente de reglement
        SUCCEEDED,  // Encaisse — seul statut qui alimente le revenu
        FAILED,     // Rejete (prelevement refuse, virement non recu)
        REFUNDED    // Rembourse apres coup
    }

    public enum PaymentMethod {
        TRANSFER,      // Virement bancaire
        CARD,          // Carte
        DIRECT_DEBIT,  // Domiciliation SEPA
        MANUAL         // Saisie manuelle (regularisation, geste commercial)
    }
}
