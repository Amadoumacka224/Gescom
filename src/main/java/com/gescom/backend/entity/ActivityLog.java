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
 * Entité de la piste d'audit : une ligne par action significative effectuée dans l'application.
 * Relie l'auteur (user), le type d'action, l'entité visée (nom + id) et une description lisible.
 * Le champ générique entity/entityId permet de tracer n'importe quel objet sans relation directe.
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
     * Entreprise proprietaire de la ligne - cle du cloisonnement multi-entreprises.
     *
     * Renseignee automatiquement a la creation par TenantEntityListener : aucun service ni
     * mapper n'a a s'en occuper, ce qui evite qu'un oubli produise une ligne orpheline.
     * Ecartee de la serialisation JSON, les controleurs ne renvoyant que des DTO.
     *
     * Seule table cloisonnee ou la colonne reste facultative : les actions du proprietaire
     * de la plateforme — a commencer par sa propre connexion — sont journalisees ici sans
     * se rattacher a une entreprise cliente.
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
