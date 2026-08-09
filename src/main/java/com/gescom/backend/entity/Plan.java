package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Formule d'abonnement proposee au catalogue.
 *
 * Les deux tarifs cohabitent au lieu d'etre derives l'un de l'autre : l'annuel porte une
 * remise (deux mois offerts dans le catalogue livre), et c'est le montant reellement
 * facture qui doit alimenter le calcul du MRR — d'ou la mensualisation explicite operee
 * par {@code Subscription.monthlyAmount()}.
 *
 * {@code maxUsers} / {@code maxProducts} a null signifient « illimite ».
 */
@Entity
@Table(name = "plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le code de la formule est obligatoire")
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank(message = "Le nom de la formule est obligatoire")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @NotNull(message = "Le tarif mensuel est obligatoire")
    @DecimalMin(value = "0.0", message = "Le tarif mensuel ne peut pas etre negatif")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @NotNull(message = "Le tarif annuel est obligatoire")
    @DecimalMin(value = "0.0", message = "Le tarif annuel ne peut pas etre negatif")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal yearlyPrice;

    /** Plafond d'utilisateurs ; null = illimite. */
    @Column
    private Integer maxUsers;

    /** Plafond d'articles au catalogue ; null = illimite. */
    @Column
    private Integer maxProducts;

    @NotNull
    @Column(nullable = false)
    private Integer trialDays = 14;

    /**
     * Une formule retiree du catalogue reste en base : les abonnements souscrits
     * continuent d'y pointer et de peser dans le MRR. Seule la souscription est fermee.
     */
    @NotNull
    @Column(nullable = false)
    private Boolean active = true;

    @NotNull
    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
