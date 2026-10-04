package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Parametres de la plateforme elle-meme.
 *
 * A ne pas confondre avec {@link Settings}, qui contient les parametres metier d'une
 * entreprise cliente (et qui est filtre par entreprise). Celui-ci ne l'est pas : il
 * n'appartient a aucune entreprise, il concerne la gestion du SaaS.
 *
 * Une seule ligne en base, avec l'id 1 (impose par un CHECK).
 *
 * Ces valeurs etaient avant des constantes dans {@code PlatformMetricsService}. On les a
 * sorties du code parce que ce sont des choix commerciaux (« a partir de quand un
 * renouvellement est "a venir" ? ») qui peuvent changer, et ca ne devrait pas demander un
 * redeploiement.
 */
@Entity
@Table(name = "platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    /** Id fixe : la seule ligne de la table. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Periode du bloc « renouvellements a venir » du tableau de bord. */
    @NotNull
    @Min(1) @Max(365)
    @Column(nullable = false)
    private Integer renewalWindowDays = 30;

    /** Combien de jours avant la fin de l'essai on affiche l'alerte. */
    @NotNull
    @Min(1) @Max(90)
    @Column(nullable = false)
    private Integer trialAlertDays = 7;

    /** Nombre de mois affiches dans la courbe des revenus. */
    @NotNull
    @Min(1) @Max(60)
    @Column(nullable = false)
    private Integer revenueHistoryMonths = 12;

    /**
     * Points enleves au score de sante pour chaque echeance impayee.
     *
     * Le score reste volontairement simple a expliquer : le pourcentage d'entreprises qui
     * fonctionnent, moins ces penalites. Un indicateur qu'on ne sait pas justifier n'a rien
     * a faire sur un tableau de bord.
     */
    @NotNull
    @Min(0) @Max(50)
    @Column(nullable = false)
    private Integer overduePenaltyPoints = 5;

    /** Points enleves pour chaque echec de paiement du mois. */
    @NotNull
    @Min(0) @Max(50)
    @Column(nullable = false)
    private Integer failedPaymentPenaltyPoints = 2;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = SINGLETON_ID;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
