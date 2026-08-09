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

@Entity
@Table(name = "settings")
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Settings implements TenantOwned {

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

    // Général
    @Column(nullable = false)
    private String language = "fr";

    @Column(nullable = false)
    private String currency = "EUR";

    @Column(nullable = false)
    private String timezone = "Europe/Brussels";

    @Column(nullable = false)
    private String dateFormat = "DD/MM/YYYY";

    // Entreprise
    @Column(nullable = false)
    private String companyName;

    private String companyEmail;
    private String companyPhone;
    private String companyAddress;
    private String companyCity;
    private String companyPostalCode;
    private String companyCountry;
    private String companyTaxId;   // N° de TVA / n° d'entreprise (BCE) — format belge : BE0XXX.XXX.XXX
    private String companyIban;    // Compte bancaire (mention légale belge pour le paiement)
    private String companyBic;

    // Facturation
    @Column(nullable = false)
    private Double taxRate = 21.0;  // Taux de TVA standard en Belgique

    @Column(nullable = false)
    private String invoicePrefix = "INV";

    @Column(nullable = false)
    private Integer invoiceNumberStart = 1000;

    @Column(nullable = false)
    private Integer paymentTerms = 30;

    @Column(columnDefinition = "TEXT")
    private String footerText;

    // Notifications
    @Column(nullable = false)
    private Boolean notifications = true;

    @Column(nullable = false)
    private Boolean emailNotifications = true;

    @Column(nullable = false)
    private Boolean orderNotifications = true;

    @Column(nullable = false)
    private Boolean stockAlerts = true;

    @Column(nullable = false)
    private Integer lowStockThreshold = 10;

    // Apparence
    @Column(nullable = false)
    private String theme = "light";

    // Metadata
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
}
