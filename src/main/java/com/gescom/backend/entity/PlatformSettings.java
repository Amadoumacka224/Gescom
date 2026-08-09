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
 * Reglages de la plateforme elle-meme.
 *
 * A ne pas confondre avec {@link Settings}, qui est le parametrage metier d'une entreprise
 * cliente — et qui, lui, est cloisonne. Celui-ci ne l'est pas : il n'appartient a aucune
 * entreprise, il decrit l'exploitation du SaaS.
 *
 * Singleton persistant, une seule ligne d'identifiant 1, garantie par un CHECK en base.
 *
 * Les valeurs portees ici etaient auparavant des constantes de
 * {@code PlatformMetricsService}. Les sortir du code n'a d'interet que parce qu'elles
 * repondent a des questions commerciales — « a partir de quand un renouvellement est-il
 * "a venir" ? » — dont la reponse varie d'un exploitant a l'autre et n'a aucune raison
 * d'exiger un redeploiement.
 */
@Entity
@Table(name = "platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    /** Identifiant fige : la ligne unique du singleton. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Horizon du bloc « renouvellements a venir » du tableau de bord. */
    @NotNull
    @Min(1) @Max(365)
    @Column(nullable = false)
    private Integer renewalWindowDays = 30;

    /** Anticipation de l'alerte de fin de periode d'essai. */
    @NotNull
    @Min(1) @Max(90)
    @Column(nullable = false)
    private Integer trialAlertDays = 7;

    /** Profondeur de la courbe de revenus encaisses. */
    @NotNull
    @Min(1) @Max(60)
    @Column(nullable = false)
    private Integer revenueHistoryMonths = 12;

    /**
     * Points retires au score de sante par echeance impayee.
     *
     * Le score reste volontairement explicable : part des entreprises operationnelles, moins
     * ces penalites. Un indicateur de synthese qu'on ne sait pas justifier devant un comite
     * n'a pas sa place sur un tableau de bord de direction.
     */
    @NotNull
    @Min(0) @Max(50)
    @Column(nullable = false)
    private Integer overduePenaltyPoints = 5;

    /** Points retires par echec de paiement du mois. */
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
