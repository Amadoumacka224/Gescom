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
 * Pas filtree par entreprise : le ticket concerne une entreprise mais fait partie de la
 * gestion de la plateforme, comme {@link Company} ou {@link Subscription}. On n'y accede
 * que par {@code /api/platform/**}.
 *
 * Le ticket est cree par l'operateur apres un appel ou un mail, les clients n'ont pas
 * d'ecran pour en creer un. C'est voulu : le contact reste humain, GESCOM garde juste une
 * trace et le suivi.
 */
@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    /** Statuts qui veulent dire que le ticket est encore a traiter. */
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

    /** Personne a contacter chez le client, si on la connait. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contact_user_id")
    private User contactUser;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opened_by_id", nullable = false)
    private User openedBy;

    /**
     * Messages du ticket, du plus ancien au plus recent.
     *
     * {@code orphanRemoval} correspond a la suppression en cascade en base : un message
     * n'existe pas sans son ticket.
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
     * Statuts d'une demande.
     *
     * RESOLVED et CLOSED sont differents exprès : le premier veut dire qu'on a repondu, le
     * second que le dossier est ferme pour de bon. Entre les deux, le client peut encore
     * revenir sans qu'on doive rouvrir un ticket.
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
