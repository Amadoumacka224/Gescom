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
 * Evenement important de la plateforme, avec un etat lu / non lu.
 *
 * Ce n'est pas la meme chose que les alertes du tableau de bord
 * ({@code PlatformDashboardResponse.PlatformAlert}) : les alertes montrent la situation
 * actuelle et sont recalculees a chaque affichage (une echeance impayee disparait des
 * qu'elle est payee). Une notification garde un evenement date, qu'on peut revoir une fois
 * le probleme regle. En gros : « qu'est-ce qui ne va pas maintenant ? » d'un cote, et
 * « que s'est-il passe pendant mon absence ? » de l'autre.
 *
 * Pas de destinataire : le back-office n'a qu'un utilisateur, le super admin. Si un jour
 * plusieurs personnes l'utilisent, il faudra un etat lu par personne. Pour l'instant
 * {@code readAt} est donc sur la ligne et pas dans une table de liaison, on verra a ce
 * moment-la.
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

    /** Type d'evenement, pour que le front le traduise au lieu de l'afficher brut. */
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

    /** Entreprise concernee ; null si l'evenement ne concerne aucune entreprise. */
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
