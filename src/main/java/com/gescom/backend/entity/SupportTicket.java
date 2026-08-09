package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Demande de support d'une entreprise cliente.
 *
 * Non cloisonnee : le ticket reference une entreprise mais releve de l'exploitation de la
 * plateforme, au meme titre que {@link Company} ou {@link Subscription}. Son unique porte
 * d'entree est {@code /api/platform/**}.
 *
 * Le ticket est ouvert par l'operateur a partir d'un appel ou d'un courriel — il n'existe
 * pas d'ecran client pour en deposer un. C'est assume : le canal reste humain, GESCOM n'en
 * garde que la trace et le suivi.
 */
@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    /** Statuts qui comptent comme un ticket encore a traiter. */
    public static final Set<TicketStatus> OPEN_STATUSES =
            Set.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "L'objet du ticket est obligatoire")
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String subject;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority = TicketPriority.NORMAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCategory category = TicketCategory.OTHER;

    /** Interlocuteur chez le client, quand il a pu etre identifie. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contact_user_id")
    private User contactUser;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opened_by_id", nullable = false)
    private User openedBy;

    /**
     * Fil de discussion, du plus ancien au plus recent.
     *
     * {@code orphanRemoval} suit la cascade de suppression posee en base : un message n'a
     * aucune existence hors de son ticket.
     */
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SupportTicketMessage> messages = new ArrayList<>();

    @Column
    private LocalDateTime resolvedAt;

    @Column
    private LocalDateTime closedAt;

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

    public boolean isOpen() {
        return OPEN_STATUSES.contains(status);
    }

    /**
     * Cycle de vie d'une demande.
     *
     * RESOLVED et CLOSED sont distincts a dessein : le premier dit que la reponse est
     * apportee, le second que le dossier est definitivement clos. Entre les deux, le client
     * peut encore revenir sans qu'il faille rouvrir un ticket.
     */
    public enum TicketStatus {
        OPEN,              // Recue, pas encore prise en charge
        IN_PROGRESS,       // En cours de traitement
        WAITING_CUSTOMER,  // En attente d'un retour du client
        RESOLVED,          // Reponse apportee
        CLOSED             // Dossier clos
    }

    public enum TicketPriority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum TicketCategory {
        TECHNICAL,  // Anomalie, question d'usage
        BILLING,    // Facturation, abonnement
        ACCOUNT,    // Comptes, acces
        FEATURE,    // Demande d'evolution
        OTHER
    }
}
