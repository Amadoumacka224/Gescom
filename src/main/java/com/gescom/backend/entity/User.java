package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Entité utilisateur, qui sert aussi de principal Spring Security (implémente UserDetails).
 * Le mot de passe est exclu de la sérialisation JSON (jamais renvoyé au client) et les
 * autorisations sont dérivées du rôle (ADMIN / CAISSIER) sous la forme « ROLE_<rôle> ».
 * Le compte est considéré « activé » uniquement si le champ active est vrai (isEnabled()).
 */
@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
public class User implements UserDetails, TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise d'appartenance — nulle pour le seul SUPER_ADMIN.
     *
     * C'est l'unique champ facultatif de tout le cloisonnement, et il porte le modele a lui
     * seul : le proprietaire de la plateforme n'appartient a aucune entreprise cliente, et
     * c'est precisement cette absence de rattachement qui lui ouvre la vue globale (voir
     * TenantContext). La coherence role / entreprise est verrouillee en base par la
     * contrainte chk_users_company_scope, pour qu'un ADMIN ne puisse jamais se retrouver
     * sans entreprise et heriter par accident de cette vue.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company ownerCompany;

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String lastName;

    @Pattern(regexp = "^$|^[0-9+\\- ]{6,20}$", message = "Format de téléphone invalide")
    @Column(length = 20)
    private String phone;

    @NotNull(message = "Le rôle est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CAISSIER;

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

    // ── Implémentation de UserDetails (contrat Spring Security) ──────────────
    // Convertit le rôle métier en autorité Spring Security (préfixe « ROLE_ » exigé par hasRole()).
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Un compte désactivé (active = false) ne peut pas se connecter — et, depuis la bascule
     * multi-entreprises, un compte dont l'entreprise n'est plus opérationnelle non plus.
     *
     * Porter la règle ici plutôt que dans le contrôleur d'authentification la rend
     * inévitable : c'est le contrat {@code UserDetails} que Spring Security interroge, de
     * sorte que suspendre une entreprise pour impayé coupe l'accès de tous ses utilisateurs
     * d'un seul geste, sans toucher à leurs comptes ni à leurs données.
     */
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return active && (ownerCompany == null || ownerCompany.isOperational());
    }

    /**
     * SUPER_ADMIN n'est pas un ADMIN plus puissant : c'est un rôle d'une autre nature.
     * L'ADMIN administre son entreprise et ne voit qu'elle ; le SUPER_ADMIN exploite la
     * plateforme, n'appartient à aucune entreprise et n'a accès à aucun écran métier —
     * son périmètre est l'espace /api/platform, et rien d'autre.
     */
    public enum Role {
        ADMIN,       // Accès complet au sein de son entreprise
        CAISSIER,    // Opérations de caisse (ventes, encaissements)
        SUPER_ADMIN  // Propriétaire de la plateforme : vue globale du parc, aucun accès métier
    }

    /** Vrai pour le propriétaire de la plateforme, seul compte non rattaché à une entreprise. */
    @JsonIgnore
    public boolean isPlatformOwner() {
        return role == Role.SUPER_ADMIN;
    }
}
