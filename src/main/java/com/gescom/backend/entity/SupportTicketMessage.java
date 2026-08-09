package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message du fil d'un ticket.
 *
 * {@code internal} separe la note de service du message destine au client. La distinction
 * n'est pas cosmetique : elle permet de consigner « client injoignable, relancer lundi »
 * sans risquer de le lui adresser le jour ou un envoi automatique sera branche. Tant que ce
 * jour n'est pas venu, rien ne part — le fil est un registre, pas une messagerie.
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

    /** Ecarte de la serialisation : le message est toujours rendu dans son ticket. */
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
