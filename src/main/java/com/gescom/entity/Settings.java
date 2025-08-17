package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un paramètre de configuration système
 * Compatible JDK 17 avec toutes les fonctionnalités modernes
 */
@Entity
@Table(name = "app_settings", indexes = {
    @Index(name = "idx_settings_category", columnList = "category"),
    @Index(name = "idx_settings_key", columnList = "setting_key", unique = true),
    @Index(name = "idx_settings_updated", columnList = "updated_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Settings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "La clé est obligatoire")
    @Size(max = 100, message = "La clé ne peut pas dépasser 100 caractères")
    @Column(name = "setting_key", unique = true, nullable = false, length = 100)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SettingCategory category = SettingCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ValueType valueType = ValueType.STRING;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Catégories de paramètres avec descriptions
     */
    public enum SettingCategory {
        GENERAL("Général", "fas fa-cog", "Paramètres généraux de l'application"),
        COMPANY("Entreprise", "fas fa-building", "Informations sur l'entreprise"),
        EMAIL("Email", "fas fa-envelope", "Configuration des emails"),
        INVOICE("Facturation", "fas fa-file-invoice", "Paramètres de facturation"),
        TAX("Fiscalité", "fas fa-percent", "Configuration des taxes"),
        SECURITY("Sécurité", "fas fa-shield-alt", "Paramètres de sécurité"),
        NOTIFICATION("Notifications", "fas fa-bell", "Gestion des notifications"),
        SYSTEM("Système", "fas fa-server", "Paramètres système avancés"),
        INTEGRATION("Intégrations", "fas fa-plug", "Intégrations tierces"),
        APPEARANCE("Apparence", "fas fa-palette", "Personnalisation de l'interface");

        private final String displayName;
        private final String icon;
        private final String description;

        SettingCategory(String displayName, String icon, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }

    /**
     * Types de valeurs supportés avec validation
     */
    public enum ValueType {
        STRING("Texte", "text", "Chaîne de caractères simple"),
        TEXT("Texte long", "textarea", "Texte multi-lignes"),
        INTEGER("Nombre entier", "number", "Nombre entier (ex: 42, -10)"),
        DECIMAL("Nombre décimal", "number", "Nombre à virgule (ex: 3.14, 19.6)"),
        BOOLEAN("Booléen", "checkbox", "Vrai ou Faux (true/false)"),
        EMAIL("Email", "email", "Adresse email valide"),
        URL("URL", "url", "Adresse web (ex: https://exemple.com)"),
        PASSWORD("Mot de passe", "password", "Mot de passe (crypté automatiquement)"),
        COLOR("Couleur", "color", "Couleur hexadécimale (ex: #FF0000)"),
        DATE("Date", "date", "Date au format YYYY-MM-DD"),
        TIME("Heure", "time", "Heure au format HH:MM"),
        JSON("JSON", "textarea", "Objet JSON valide"),
        FILE_PATH("Chemin", "text", "Chemin vers un fichier ou dossier"),
        LIST("Liste", "textarea", "Liste de valeurs séparées par des virgules");

        private final String displayName;
        private final String inputType;
        private final String description;

        ValueType(String displayName, String inputType, String description) {
            this.displayName = displayName;
            this.inputType = inputType;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getInputType() { return inputType; }
        public String getDescription() { return description; }
    }

    // Constructeurs
    public Settings() {}

    public Settings(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Settings(String key, String value, String description, SettingCategory category, ValueType valueType) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.category = category;
        this.valueType = valueType;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SettingCategory getCategory() { return category; }
    public void setCategory(SettingCategory category) { this.category = category; }

    public ValueType getValueType() { return valueType; }
    public void setValueType(ValueType valueType) { this.valueType = valueType; }

    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

    public Boolean getIsEncrypted() { return isEncrypted; }
    public void setIsEncrypted(Boolean isEncrypted) { this.isEncrypted = isEncrypted; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Méthodes utilitaires avec validation
    public Boolean getBooleanValue() {
        if (valueType != ValueType.BOOLEAN || value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    public void setBooleanValue(Boolean boolValue) {
        this.value = boolValue != null ? boolValue.toString() : "false";
        this.valueType = ValueType.BOOLEAN;
    }

    public Integer getIntegerValue() {
        if (valueType != ValueType.INTEGER || value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setIntegerValue(Integer intValue) {
        this.value = intValue != null ? intValue.toString() : "0";
        this.valueType = ValueType.INTEGER;
    }

    public Double getDecimalValue() {
        if (valueType != ValueType.DECIMAL || value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void setDecimalValue(Double decimalValue) {
        this.value = decimalValue != null ? decimalValue.toString() : "0.0";
        this.valueType = ValueType.DECIMAL;
    }

    /**
     * Valide la valeur selon son type
     */
    public boolean isValidValue() {
        if (value == null) return true;
        
        return switch (valueType) {
            case INTEGER -> {
                try {
                    Integer.parseInt(value.trim());
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case DECIMAL -> {
                try {
                    Double.parseDouble(value.trim());
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case EMAIL -> value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
            case URL -> value.matches("^https?://.*");
            case COLOR -> value.matches("^#[0-9A-Fa-f]{6}$");
            case JSON -> {
                try {
                    // Validation JSON basique
                    if (value.trim().startsWith("{") && value.trim().endsWith("}")) {
                        yield true;
                    }
                    if (value.trim().startsWith("[") && value.trim().endsWith("]")) {
                        yield true;
                    }
                    yield false;
                } catch (Exception e) {
                    yield false;
                }
            }
            default -> true; // Pour STRING, TEXT, PASSWORD, FILE_PATH, LIST, DATE, TIME
        };
    }

    /**
     * Obtient la valeur formatée pour l'affichage
     */
    public String getDisplayValue() {
        if (value == null) return "";
        
        return switch (valueType) {
            case PASSWORD -> "••••••••";
            case BOOLEAN -> getBooleanValue() ? "✓ Activé" : "✗ Désactivé";
            case LIST -> value.replace(",", ", ");
            case JSON -> value.length() > 50 ? value.substring(0, 50) + "..." : value;
            case TEXT -> value.length() > 100 ? value.substring(0, 100) + "..." : value;
            default -> value;
        };
    }

    // Méthodes equals et hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return Objects.equals(key, settings.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "Settings{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", category=" + category +
                ", valueType=" + valueType +
                ", isSystem=" + isSystem +
                '}';
    }
}