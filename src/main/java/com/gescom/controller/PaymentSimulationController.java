package com.gescom.controller;

import com.gescom.entity.Invoice;
import com.gescom.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Contrôleur pour le paiement (simulation sans vraie transaction)
 */
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentSimulationController {

    private final InvoiceRepository invoiceRepository;

    /**
     * Page d'accueil - Recherche de facture pour paiement
     */
    @GetMapping
    public String paymentHome(Model model) {
        try {
            // Vérifier s'il y a des factures disponibles
            var allInvoices = invoiceRepository.findAll();
            System.out.println("Nombre total de factures dans la base: " + allInvoices.size());
            
            if (!allInvoices.isEmpty()) {
                var firstInvoice = allInvoices.get(0);
                System.out.println("Exemple de facture disponible: " + firstInvoice.getInvoiceNumber());
            }
            
            model.addAttribute("message", "Paiement sécurisé - Démonstration");
            return "payment/search";
        } catch (Exception e) {
            System.err.println("Erreur dans paymentHome: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Erreur de chargement de la page");
            return "payment/search";
        }
    }

    /**
     * Recherche de facture pour simulation
     */
    @PostMapping("/search")
    public String searchInvoice(@RequestParam("invoiceNumber") String invoiceNumber,
                               RedirectAttributes redirectAttributes) {
        
        try {
            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Veuillez saisir un numéro de facture");
                return "redirect:/payment";
            }
            
            System.out.println("Recherche de la facture: " + invoiceNumber.trim());
            
            // Recherche simple de la facture
            Optional<Invoice> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber.trim());
            
            System.out.println("Résultat de la recherche: " + (invoiceOpt.isPresent() ? "Trouvée" : "Non trouvée"));
            
            if (invoiceOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Facture introuvable: " + invoiceNumber);
                return "redirect:/payment";
            }
            
            Invoice invoice = invoiceOpt.get();
            System.out.println("Facture trouvée - ID: " + invoice.getId() + ", Numéro: " + invoice.getInvoiceNumber());
            
            // Redirection vers le paiement
            redirectAttributes.addAttribute("invoiceId", invoice.getId());
            return "redirect:/payment/pay";
            
        } catch (Exception e) {
            System.err.println("Erreur dans searchInvoice: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Une erreur technique est survenue. Veuillez réessayer.");
            return "redirect:/payment";
        }
    }

    /**
     * Page de paiement
     */
    @GetMapping("/pay")
    public String paymentPage(@RequestParam("invoiceId") Long invoiceId, Model model) {
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isEmpty()) {
            model.addAttribute("error", "Facture non trouvée");
            return "redirect:/payment";
        }
        
        Invoice invoice = invoiceOpt.get();
        model.addAttribute("invoice", invoice);
        return "payment/pay";
    }

    /**
     * Traitement du paiement
     */
    @PostMapping("/process")
    public String processPayment(@RequestParam("invoiceId") Long invoiceId,
                                @RequestParam("paymentMethod") String paymentMethod,
                                @RequestParam(value = "customerName", required = false) String customerName,
                                @RequestParam(value = "customerEmail", required = false) String customerEmail,
                                RedirectAttributes redirectAttributes) {
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
            return "redirect:/payment";
        }
        
        Invoice invoice = invoiceOpt.get();
        
        // Traitement : marquer la facture comme payée
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoice.setPaidAmount(invoice.getTotalAmount());
        invoiceRepository.save(invoice);
        
        // Informations de paiement
        redirectAttributes.addFlashAttribute("success", "✅ PAIEMENT RÉUSSI");
        redirectAttributes.addFlashAttribute("paymentInfo", 
            "Facture: " + invoice.getInvoiceNumber() + " | " +
            "Montant: " + invoice.getTotalAmount() + "€ | " +
            "Méthode: " + paymentMethod + " | " +
            "Client: " + (customerName != null ? customerName : "N/A") + " | " +
            "Traité le: " + LocalDateTime.now());
            
        return "redirect:/payment/success?invoiceId=" + invoiceId;
    }

    /**
     * Page de succès de paiement
     */
    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("invoiceId") Long invoiceId, Model model) {
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            model.addAttribute("invoice", invoiceOpt.get());
        }
        
        return "payment/success";
    }

    /**
     * Liste des factures pour tests
     */
    @GetMapping("/invoices")
    @ResponseBody
    public String listInvoices() {
        StringBuilder response = new StringBuilder();
        response.append("=== FACTURES DISPONIBLES POUR PAIEMENT ===\n\n");
        
        var invoices = invoiceRepository.findAll();
        
        if (invoices.isEmpty()) {
            response.append("❌ AUCUNE FACTURE DISPONIBLE!\n");
            response.append("→ Créez d'abord des factures via l'admin: http://localhost:9090/login\n");
        } else {
            response.append("📋 LISTE DES FACTURES:\n");
            response.append("--------------------------------------------------\n");
            
            for (Invoice invoice : invoices) {
                response.append("• ").append(invoice.getInvoiceNumber())
                       .append(" | Statut: ").append(invoice.getStatus())
                       .append(" | Montant: ").append(invoice.getTotalAmount()).append("€")
                       .append("\n");
            }
            
            response.append("\n💡 POUR TESTER:\n");
            response.append("→ Copiez un numéro de facture\n");
            response.append("→ Allez sur: http://localhost:9090/payment\n");
            response.append("→ Collez le numéro et suivez le paiement\n");
        }
        
        return response.toString();
    }
}