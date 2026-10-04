package com.gescom.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Journal d'activité : une ligne par action importante faite dans l'application.
 * On garde l'auteur (user), le type d'action, l'objet concerné (nom + id) et une description.
 * Le couple entity/entityId permet de suivre n'importe quel objet sans relation directe.
 */
@Entity
@Table(name = "activity_logs")
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise a qui appartient la ligne, c'est ce qui separe les donnees des entreprises.
     *
     * Remplie automatiquement a la creation par TenantEntityListener : ni les services ni
     * les mappers n'ont a s'en occuper, donc pas de risque d'oubli. Pas envoyee en JSON
     * (les controleurs ne renvoient que des DTO).
     *
     * C'est la seule table ou cette colonne peut etre vide : les actions du super admin
     * (a commencer par sa connexion) sont enregistrees ici sans entreprise.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company ownerCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    @Column(nullable = false, length = 100)
    private String entity;

    @Column
    private Long entityId;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ActionType {
        CREATE,     // Création
        UPDATE,     // Modification
        DELETE,     // Suppression
        VIEW,       // Consultation
        LOGIN,      // Connexion
        LOGOUT,     // Déconnexion
        SALE,       // Vente
        PAYMENT,    // Paiement
        STOCK_IN,   // Entrée stock
        STOCK_OUT,  // Sortie stock
        EXPORT,     // Export
        IMPORT      // Import
    }
}
