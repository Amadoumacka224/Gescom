package com.gescom.backend.entity;

import jakarta.persistence.*;
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
 * Entreprise cliente de la plateforme (le « tenant »).
 *
 * A ne pas confondre avec {@link Client}, qui represente les clients du magasin d'une
 * entreprise : ici ce sont les societes qui s'abonnent a GESCOM. Toutes les entites metier
 * ont un lien vers elle (voir {@code TenantOwned}) et aucune requete ne mélange les
 * entreprises, sauf pour le super admin.
 *
 * Le {@code status} represente l'etat commercial du compte, different du statut de
 * l'{@link Subscription} : une entreprise peut etre SUSPENDED (acces bloque pour impaye)
 * tout en ayant un abonnement PAST_DUE qu'on essaie de se faire payer.
 */
@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Identifiant lisible fixe, cree a la creation. Il sert de reference pour le support et
     * cote technique : contrairement a la raison sociale, il ne change jamais.
     */
    @NotBlank(message = "L'identifiant de l'entreprise est obligatoire")
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,78}[a-z0-9])$",
             message = "L'identifiant doit etre en minuscules, chiffres et tirets")
    @Column(nullable = false, unique = true, length = 80, updatable = false)
    private String slug;

    @NotBlank(message = "L'email de l'entreprise est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String email;

    @Pattern(regexp = "^$|^[0-9+\\- ]{6,30}$", message = "Format de telephone invalide")
    @Column(length = 30)
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

    @Column(nullable = false, length = 100)
    private String country = "Belgique";

    /** Numero de TVA / BCE, format belge : BE0XXX.XXX.XXX. */
    @Size(max = 50)
    @Column(length = 50)
    private String taxId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.TRIAL;

    /** Fin de la periode d'essai ; null des que l'entreprise est passee en payant. */
    @Column
    private LocalDateTime trialEndsAt;

    @Column
    private LocalDateTime canceledAt;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

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

    /**
     * Etat commercial du compte. Seuls TRIAL et ACTIVE permettent aux utilisateurs de se
     * connecter ; SUSPENDED et CANCELED bloquent sans rien supprimer, donc en reactivant on
     * retrouve le compte tel quel.
     */
    public enum CompanyStatus {
        TRIAL,      // Periode d'essai en cours
        ACTIVE,     // Abonnement en regle
        SUSPENDED,  // Acces coupe (impaye, litige) - donnees conservees
        CANCELED    // Resiliee - donnees conservees pour la duree de retention legale
    }

    /** Vrai si les utilisateurs de cette entreprise ont le droit de se connecter. */
    public boolean isOperational() {
        return status == CompanyStatus.TRIAL || status == CompanyStatus.ACTIVE;
    }
}
