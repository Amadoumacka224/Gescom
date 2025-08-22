package com.gescom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Formulaire de recherche de facture pour paiement externe
 */
@Data
@NoArgsConstructor
public class InvoiceSearchForm {
    
    @NotBlank(message = "Le numéro de facture est obligatoire")
    @Size(min = 3, max = 50, message = "Le numéro de facture doit contenir entre 3 et 50 caractères")
    @Pattern(
        regexp = "^[A-Z0-9\\-_]+$", 
        message = "Le numéro de facture ne peut contenir que des lettres majuscules, des chiffres, des tirets et des underscores"
    )
    private String invoiceNumber;
    
    public InvoiceSearchForm(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }
}