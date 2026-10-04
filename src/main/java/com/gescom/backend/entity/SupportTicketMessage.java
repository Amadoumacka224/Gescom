package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message d'un ticket.
 *
 * {@code internal} distingue une note interne d'un message pour le client. C'est utile :
 * on peut noter « client injoignable, relancer lundi » sans risquer de lui envoyer le jour
 * ou on ajoutera un envoi automatique. Pour l'instant rien n'est envoye, c'est juste un
 * historique, pas une messagerie.
 */
@Entity
@Table(name = "support_ticket_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pas mis dans le JSON : le message est toujours renvoye avec son ticket. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @NotBlank(message = "Le message ne peut pas etre vide")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean internal = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
