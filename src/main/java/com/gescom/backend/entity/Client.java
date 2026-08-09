package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité client (destinataire des commandes).
 * Distingue les particuliers des entreprises via le type, et applique des contraintes de
 * validation sur les coordonnées (email unique et valide, format de téléphone, champs requis).
 */
@Entity
@Table(name = "clients", uniqueConstraints = @UniqueConstraint(name = "uq_clients_company_email", columnNames = {"company_id", "email"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Client implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise proprietaire de la ligne - cle du cloisonnement multi-entreprises.
     *
     * Renseignee automatiquement a la creation par TenantEntityListener : aucun service ni
     * mapper n'a a s'en occuper, ce qui evite qu'un oubli produise une ligne orpheline.
     * Ecartee de la serialisation JSON, les controleurs ne renvoyant que des DTO.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company ownerCompany;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String lastName;

    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    @Column(length = 100)
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "Format de téléphone invalide")
    @Column(nullable = false, length = 20)
    private String phone;

    @Size(max = 255)
    @Column(length = 255)
    private String address;

    @Size(max = 100)
    @Column(length = 100)
    private String city;

    @Size(max = 20)
    @Column(length = 20)
    private String postalCode;

    @Size(max = 100)
    @Column(length = 100)
    private String country;

    @Size(max = 50)
    @Column(length = 50)
    private String company;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType type = ClientType.PARTICULIER;

    @NotNull
    @Column(nullable = false)
    private Boolean active = true;

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

    public enum ClientType {
        PARTICULIER, ENTREPRISE
    }
}
