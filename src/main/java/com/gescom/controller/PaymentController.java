package com.gescom.controller;

import com.gescom.dto.PaymentForm;
import com.gescom.entity.ExternalPayment;
import com.gescom.entity.Invoice;
import com.gescom.repository.InvoiceRepository;
import com.gescom.service.ExternalPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Contrôleur principal pour les paiements externes
 */
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final InvoiceRepository invoiceRepository;
    private final ExternalPaymentService externalPaymentService;

    /**
     * Page de recherche de facture
     */
    @GetMapping
    public String searchPage(Model model) {
        return "payment/search";
    }

    /**
     * Recherche de facture pour paiement
     */
    @PostMapping("/search")
    public String searchInvoice(@RequestParam("invoiceNumber") String invoiceNumber,
                               RedirectAttributes redirectAttributes) {
        
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez saisir un numéro de facture");
            return "redirect:/payment";
        }
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber.trim());
        
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Facture introuvable: " + invoiceNumber);
            return "redirect:/payment";
        }
        
        Invoice invoice = invoiceOpt.get();
        
        // Vérifier si la facture est déjà payée
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            redirectAttributes.addFlashAttribute("error", "Cette facture est déjà payée");
            return "redirect:/payment";
        }
        
        return "redirect:/payment/select-method?invoiceNumber=" + invoice.getInvoiceNumber();
    }

    /**
     * Page de sélection du mode de paiement
     */
    @GetMapping("/select-method")
    public String selectPaymentMethod(@RequestParam("invoiceNumber") String invoiceNumber,
                                    Model model) {
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        
        if (invoiceOpt.isEmpty()) {
            model.addAttribute("error", "Facture non trouvée");
            return "payment/search";
        }
        
        Invoice invoice = invoiceOpt.get();
        
        // Vérifier si la facture est déjà payée
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            model.addAttribute("error", "Cette facture est déjà payée");
            return "payment/search";
        }
        
        BigDecimal remainingAmount = invoice.getRemainingAmount();
        
        PaymentForm paymentForm = new PaymentForm(invoice.getInvoiceNumber());
        paymentForm.setAmount(remainingAmount);
        
        model.addAttribute("invoice", invoice);
        model.addAttribute("remainingAmount", remainingAmount);
        model.addAttribute("paymentForm", paymentForm);
        
        return "payment/select-method";
    }

    /**
     * Traitement de l'initiation du paiement
     */
    @PostMapping("/initiate")
    public String initiatePayment(@Valid @ModelAttribute PaymentForm paymentForm,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        
        log.info("Initiation du paiement pour la facture: {}", paymentForm.getInvoiceNumber());
        
        // Rechercher la facture
        Optional<Invoice> invoiceOpt = invoiceRepository.findByInvoiceNumber(paymentForm.getInvoiceNumber());
        
        if (invoiceOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Facture non trouvée");
            return "redirect:/payment";
        }
        
        Invoice invoice = invoiceOpt.get();
        
        // Validation des erreurs du formulaire
        if (bindingResult.hasErrors()) {
            model.addAttribute("invoice", invoice);
            model.addAttribute("remainingAmount", invoice.getRemainingAmount());
            return "payment/select-method";
        }
        
        try {
            // Traitement selon le mode de paiement
            if (paymentForm.getPaymentMethod() == ExternalPayment.PaymentMethod.CASH) {
                // Paiement en espèces - validation immédiate
                return processCashPayment(invoice, paymentForm, redirectAttributes);
            } else if (paymentForm.getPaymentMethod() == ExternalPayment.PaymentMethod.VISA || 
                      paymentForm.getPaymentMethod() == ExternalPayment.PaymentMethod.MASTERCARD) {
                // Paiement par carte - redirection vers l'interface de traitement
                return processCardPayment(invoice, paymentForm, redirectAttributes);
            } else {
                redirectAttributes.addFlashAttribute("error", "Mode de paiement non supporté");
                return "redirect:/payment/select-method?invoiceNumber=" + invoice.getInvoiceNumber();
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de l'initiation du paiement", e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors du traitement du paiement");
            return "redirect:/payment/select-method?invoiceNumber=" + invoice.getInvoiceNumber();
        }
    }

    /**
     * Traitement du paiement en espèces
     */
    private String processCashPayment(Invoice invoice, PaymentForm paymentForm, RedirectAttributes redirectAttributes) {
        log.info("Traitement du paiement en espèces pour la facture: {}", invoice.getInvoiceNumber());
        
        try {
            // Créer le paiement externe
            ExternalPayment payment = externalPaymentService.initiatePayment(
                invoice,
                ExternalPayment.PaymentMethod.CASH,
                paymentForm.getAmount(),
                paymentForm.getCustomerEmail(),
                paymentForm.getCustomerName(),
                null // clientIp - à récupérer depuis la requête si nécessaire
            );
            
            // Marquer le paiement comme réussi immédiatement (espèces)
            payment.markAsCompleted();
            externalPaymentService.savePayment(payment);
            
            // Mettre à jour le statut de la facture
            updateInvoiceStatusAfterPayment(invoice, paymentForm.getAmount());
            
            redirectAttributes.addFlashAttribute("success", "Paiement en espèces validé avec succès");
            redirectAttributes.addFlashAttribute("paymentMethod", "Espèces");
            redirectAttributes.addFlashAttribute("amount", paymentForm.getAmount());
            
            return "redirect:/payment/success?invoiceId=" + invoice.getId();
            
        } catch (Exception e) {
            log.error("Erreur lors du paiement en espèces", e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la validation du paiement en espèces");
            return "redirect:/payment/select-method?invoiceNumber=" + invoice.getInvoiceNumber();
        }
    }

    /**
     * Traitement du paiement par carte
     */
    private String processCardPayment(Invoice invoice, PaymentForm paymentForm, RedirectAttributes redirectAttributes) {
        log.info("Traitement du paiement par carte pour la facture: {}", invoice.getInvoiceNumber());
        
        try {
            // Créer le paiement externe
            ExternalPayment payment = externalPaymentService.initiatePayment(
                invoice,
                paymentForm.getPaymentMethod(),
                paymentForm.getAmount(),
                paymentForm.getCustomerEmail(),
                paymentForm.getCustomerName(),
                null // clientIp
            );
            
            // Pour la simulation, on va directement marquer le paiement comme réussi
            // Dans un vrai système, ici on redirigerait vers Stripe ou un autre processeur
            payment.markAsCompleted();
            externalPaymentService.savePayment(payment);
            
            // Mettre à jour le statut de la facture
            updateInvoiceStatusAfterPayment(invoice, paymentForm.getAmount());
            
            redirectAttributes.addFlashAttribute("success", "Paiement par carte validé avec succès");
            redirectAttributes.addFlashAttribute("paymentMethod", paymentForm.getPaymentMethod().getDisplayName());
            redirectAttributes.addFlashAttribute("amount", paymentForm.getAmount());
            
            return "redirect:/payment/success?invoiceId=" + invoice.getId();
            
        } catch (Exception e) {
            log.error("Erreur lors du paiement par carte", e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors du traitement du paiement par carte");
            return "redirect:/payment/select-method?invoiceNumber=" + invoice.getInvoiceNumber();
        }
    }

    /**
     * Met à jour le statut de la facture après un paiement réussi
     */
    private void updateInvoiceStatusAfterPayment(Invoice invoice, BigDecimal paidAmount) {
        // Ajouter le montant payé
        BigDecimal currentPaidAmount = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        invoice.setPaidAmount(currentPaidAmount.add(paidAmount));
        
        // Mettre à jour le statut selon le montant payé
        if (invoice.isFullyPaid()) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaymentDate(LocalDate.now());
        } else if (invoice.isPartiallyPaid()) {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIAL);
        }
        
        // Sauvegarder les modifications
        invoiceRepository.save(invoice);
        
        log.info("Statut de la facture {} mis à jour: {}", invoice.getInvoiceNumber(), invoice.getStatus());
    }

    /**
     * Page de succès de paiement
     */
    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("invoiceId") Long invoiceId, Model model) {
        
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            model.addAttribute("invoice", invoice);
        }
        
        return "payment/success";
    }

    /**
     * Page d'annulation de paiement
     */
    @GetMapping("/cancel")
    public String paymentCancel(Model model) {
        return "payment/cancel";
    }
}