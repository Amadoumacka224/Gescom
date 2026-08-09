package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Evenement notable de la plateforme, avec etat de lecture.
 *
 * A distinguer des alertes du tableau de bord ({@code PlatformDashboardResponse.PlatformAlert}) :
 * celles-ci decrivent l'etat courant du parc et sont recalculees a chaque affichage — une
 * echeance impayee disparait des qu'elle est reglee. Une notification consigne au contraire
 * un fait date, qui reste consultable une fois la situation resolue. Les deux repondent a
 * des questions differentes : « qu'est-ce qui ne va pas maintenant ? » et « que s'est-il
 * passe pendant mon absence ? ».
 *
 * Aucun destinataire n'est modelise : le back-office n'a qu'un utilisateur, le proprietaire.
 * Le jour ou l'exploitation se fera a plusieurs, il faudra un etat de lecture par personne
 * — d'ou {@code readAt} porte par la ligne et non par une table de liaison, choix a revoir
 * a ce moment-la et pas avant.
 */
@Entity
@Table(name = "platform_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nature de l'evenement, pour que l'interface le traduise plutot que l'afficher brut. */
    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.INFO;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @Size(max = 500)
    @Column(length = 500)
    private String message;

    /** Entreprise concernee ; nul pour un evenement qui n'en vise aucune. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    /** Cible facultative, pour proposer un lien vers l'ecran concerne. */
    @Size(max = 50)
    @Column(length = 50)
    private String entity;

    @Column
    private Long entityId;

    @Column
    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isRead() {
        return readAt != null;
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }
}
