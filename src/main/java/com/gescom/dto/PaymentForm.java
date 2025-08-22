package com.gescom.dto;

import com.gescom.entity.ExternalPayment;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Formulaire de saisie des informations de paiement
 */
@Data
@NoArgsConstructor
public class PaymentForm {
    
    @NotBlank(message = "Le numéro de facture est obligatoire")
    private String invoiceNumber;
    
    @NotNull(message = "Veuillez sélectionner un mode de paiement")
    private ExternalPayment.PaymentMethod paymentMethod;
    
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    @DecimalMax(value = "999999.99", message = "Le montant ne peut pas dépasser 999999.99")
    private BigDecimal amount;
    
    @NotBlank(message = "Le nom du client est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String customerName;
    
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email n'est pas valide")
    @Size(max = 255, message = "L'email ne peut pas dépasser 255 caractères")
    private String customerEmail;
    
    private String customerPhone;
    
    @AssertTrue(message = "Vous devez accepter les conditions générales")
    private boolean acceptTerms;
    
    @AssertTrue(message = "Vous devez accepter la politique de confidentialité")
    private boolean acceptPrivacy;
    
    public PaymentForm(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }
}