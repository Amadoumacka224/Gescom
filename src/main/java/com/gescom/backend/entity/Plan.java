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
 * Formule d'abonnement.
 *
 * On garde les deux prix au lieu de calculer l'un a partir de l'autre : l'annuel a une remise
 * (deux mois offerts dans les formules de base), et c'est le montant vraiment facture qui
 * doit compter dans le MRR. D'ou le calcul mensuel fait dans {@code Subscription.monthlyAmount()}.
 *
 * {@code maxUsers} / {@code maxProducts} a null = illimite.
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

    /** Nombre max d'utilisateurs ; null = illimite. */
    @Column
    private Integer maxUsers;

    /** Nombre max de produits ; null = illimite. */
    @Column
    private Integer maxProducts;

    @NotNull
    @Column(nullable = false)
    private Integer trialDays = 14;

    /**
     * Une formule desactivee reste en base : les abonnements existants pointent toujours
     * dessus et comptent dans le MRR. On ne peut juste plus y souscrire.
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
