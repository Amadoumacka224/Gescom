package com.gescom.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductImportDto {
    
    private int lineNumber;
    
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String name;
    
    @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères")
    private String description;
    
    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être supérieur à 0")
    @Digits(integer = 10, fraction = 2, message = "Le prix doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal price;
    
    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 0, message = "La quantité ne peut pas être négative")
    private Integer quantity;
    
    @Size(max = 100, message = "La référence ne peut pas dépasser 100 caractères")
    private String reference;
    
    @Size(max = 100, message = "La catégorie ne peut pas dépasser 100 caractères")
    private String category;
    
    @Size(max = 100, message = "Le fournisseur ne peut pas dépasser 100 caractères")
    private String supplier;
    
    private List<String> validationErrors = new ArrayList<>();
    
    // Constructeurs
    public ProductImportDto() {}
    
    public ProductImportDto(String name, BigDecimal price, Integer quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    
    // Getters et Setters
    public int getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description != null ? description.trim() : null;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public String getReference() {
        return reference;
    }
    
    public void setReference(String reference) {
        this.reference = reference != null ? reference.trim() : null;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category != null ? category.trim() : null;
    }
    
    public String getSupplier() {
        return supplier;
    }
    
    public void setSupplier(String supplier) {
        this.supplier = supplier != null ? supplier.trim() : null;
    }
    
    public List<String> getValidationErrors() {
        return validationErrors;
    }
    
    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }
    
    public void addValidationError(String error) {
        if (this.validationErrors == null) {
            this.validationErrors = new ArrayList<>();
        }
        this.validationErrors.add(error);
    }
    
    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
    
    // Méthodes utilitaires
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               price != null && price.compareTo(BigDecimal.ZERO) > 0 &&
               quantity != null && quantity >= 0;
    }
    
    public boolean isEmpty() {
        return (name == null || name.trim().isEmpty()) &&
               (description == null || description.trim().isEmpty()) &&
               price == null &&
               quantity == null &&
               (reference == null || reference.trim().isEmpty()) &&
               (category == null || category.trim().isEmpty()) &&
               (supplier == null || supplier.trim().isEmpty());
    }
    
    @Override
    public String toString() {
        return String.format("ProductImportDto{line=%d, name='%s', price=%s, quantity=%d, reference='%s'}",
                lineNumber, name, price, quantity, reference);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ProductImportDto that = (ProductImportDto) o;
        
        if (lineNumber != that.lineNumber) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (quantity != null ? !quantity.equals(that.quantity) : that.quantity != null) return false;
        return reference != null ? reference.equals(that.reference) : that.reference == null;
    }
    
    @Override
    public int hashCode() {
        int result = lineNumber;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (quantity != null ? quantity.hashCode() : 0);
        result = 31 * result + (reference != null ? reference.hashCode() : 0);
        return result;
    }
}