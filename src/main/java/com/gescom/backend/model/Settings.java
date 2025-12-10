package com.gescom.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Settings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Général
    @Column(nullable = false)
    private String language = "fr";

    @Column(nullable = false)
    private String currency = "EUR";

    @Column(nullable = false)
    private String timezone = "Europe/Paris";

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
    private String companyTaxId;

    // Facturation
    @Column(nullable = false)
    private Double taxRate = 20.0;

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
