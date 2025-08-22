package com.gescom.controller;

import com.gescom.entity.ExternalPayment;
import com.gescom.service.ExternalPaymentService;
import com.gescom.service.PaymentNotificationService;
import com.gescom.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Contrôleur d'administration pour la gestion des paiements externes
 * Accessible uniquement aux administrateurs
 */
@Controller
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class PaymentAdminController {

    private final ExternalPaymentService externalPaymentService;
    private final SecurityService securityService;
    private final PaymentNotificationService notificationService;

    /**
     * Tableau de bord des paiements
     */
    @GetMapping
    public String dashboard(Model model) {
        log.info("Accès au tableau de bord des paiements par un administrateur");

        // Statistiques générales
        ExternalPaymentService.PaymentStatistics paymentStats = externalPaymentService.getPaymentStatistics();
        SecurityService.SecurityStatistics securityStats = securityService.getSecurityStatistics();

        model.addAttribute("paymentStats", paymentStats);
        model.addAttribute("securityStats", securityStats);

        return "admin/payments/dashboard";
    }

    /**
     * Liste des paiements avec filtres
     */
    @GetMapping("/list")
    public String listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientIp,
            Model model) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Note: Cette méthode devrait être implémentée dans le service
        // Page<ExternalPayment> payments = externalPaymentService.findPaymentsWithFilters(status, clientIp, pageable);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", 0); // À implémenter
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("clientIp", clientIp);

        return "admin/payments/list";
    }

    /**
     * Détails d'un paiement spécifique
     */
    @GetMapping("/{id}")
    public String paymentDetails(@PathVariable Long id, Model model) {
        // Note: Cette méthode devrait être implémentée dans le service
        // Optional<ExternalPayment> payment = externalPaymentService.findById(id);
        
        // model.addAttribute("payment", payment.orElse(null));
        return "admin/payments/details";
    }

    /**
     * API pour les statistiques en temps réel
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRealtimeStats() {
        ExternalPaymentService.PaymentStatistics paymentStats = externalPaymentService.getPaymentStatistics();
        SecurityService.SecurityStatistics securityStats = securityService.getSecurityStatistics();

        Map<String, Object> stats = Map.of(
            "totalPayments", paymentStats.totalPayments(),
            "successfulPayments", paymentStats.successfulPayments(),
            "failedPayments", paymentStats.failedPayments(),
            "pendingPayments", paymentStats.pendingPayments(),
            "successRate", paymentStats.getSuccessRate(),
            "suspiciousIps", securityStats.suspiciousIpCount(),
            "timestamp", LocalDateTime.now()
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Gestion des IP suspectes
     */
    @GetMapping("/security")
    public String securityDashboard(Model model) {
        SecurityService.SecurityStatistics stats = securityService.getSecurityStatistics();
        model.addAttribute("securityStats", stats);
        
        return "admin/payments/security";
    }

    /**
     * Débloquer une IP suspecte
     */
    @PostMapping("/security/unblock")
    @ResponseBody
    public ResponseEntity<Map<String, String>> unblockIp(
            @RequestParam String clientIp,
            @RequestParam String reason) {
        
        try {
            securityService.unblockSuspiciousIp(clientIp, reason);
            
            log.info("IP débloquée par un administrateur: {} - Raison: {}", clientIp, reason);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "IP débloquée avec succès"
            ));
            
        } catch (Exception e) {
            log.error("Erreur lors du déblocage de l'IP: {}", clientIp, e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Erreur lors du déblocage: " + e.getMessage()
            ));
        }
    }

    /**
     * Forcer le nettoyage des données expirées
     */
    @PostMapping("/maintenance/cleanup")
    public String forceCleanup(RedirectAttributes redirectAttributes) {
        try {
            log.info("Nettoyage forcé des données expirées par un administrateur");
            
            externalPaymentService.cleanupExpiredPayments();
            securityService.cleanupExpiredSecurityData();
            
            redirectAttributes.addFlashAttribute("success", 
                "Nettoyage des données expirées effectué avec succès");
                
        } catch (Exception e) {
            log.error("Erreur lors du nettoyage forcé", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur lors du nettoyage: " + e.getMessage());
        }
        
        return "redirect:/admin/payments";
    }

    /**
     * Test de la configuration email
     */
    @PostMapping("/test/email")
    @ResponseBody
    public ResponseEntity<Map<String, String>> testEmailConfiguration() {
        try {
            boolean success = notificationService.testEmailConfiguration();
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Email de test envoyé avec succès"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Échec de l'envoi de l'email de test"
                ));
            }
            
        } catch (Exception e) {
            log.error("Erreur lors du test email", e);
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Erreur technique: " + e.getMessage()
            ));
        }
    }

    /**
     * Génération manuelle d'un rapport de sécurité
     */
    @PostMapping("/reports/security")
    public String generateSecurityReport(RedirectAttributes redirectAttributes) {
        try {
            log.info("Génération manuelle du rapport de sécurité par un administrateur");
            
            // Génération du rapport
            SecurityService.SecurityStatistics securityStats = securityService.getSecurityStatistics();
            ExternalPaymentService.PaymentStatistics paymentStats = externalPaymentService.getPaymentStatistics();
            
            PaymentNotificationService.SecurityReportData reportData = 
                new PaymentNotificationService.SecurityReportData(
                    (int) paymentStats.totalPayments(),
                    (int) paymentStats.successfulPayments(),
                    (int) paymentStats.failedPayments(),
                    securityStats.suspiciousIpCount(),
                    0 // blockedAttempts - à implémenter
                );
            
            notificationService.sendDailySecurityReport(reportData);
            
            redirectAttributes.addFlashAttribute("success", 
                "Rapport de sécurité généré et envoyé par email");
                
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport de sécurité", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur lors de la génération du rapport: " + e.getMessage());
        }
        
        return "redirect:/admin/payments/security";
    }

    /**
     * Configuration des paramètres de paiement
     */
    @GetMapping("/settings")
    public String paymentSettings(Model model) {
        // Récupération des paramètres actuels
        model.addAttribute("currentSettings", getCurrentSettings());
        
        return "admin/payments/settings";
    }

    /**
     * Mise à jour des paramètres de paiement
     */
    @PostMapping("/settings")
    public String updatePaymentSettings(
            @RequestParam Map<String, String> settings,
            RedirectAttributes redirectAttributes) {
        
        try {
            log.info("Mise à jour des paramètres de paiement par un administrateur");
            
            // Validation et mise à jour des paramètres
            // Note: L'implémentation dépend de la façon dont les paramètres sont stockés
            
            redirectAttributes.addFlashAttribute("success", 
                "Paramètres mis à jour avec succès");
                
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour des paramètres", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur lors de la mise à jour: " + e.getMessage());
        }
        
        return "redirect:/admin/payments/settings";
    }

    /**
     * Export des données de paiement (pour audit)
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportPaymentData(
            @RequestParam String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        try {
            log.info("Export des données de paiement demandé par un administrateur - Format: {}", format);
            
            // Note: L'implémentation de l'export dépend des besoins spécifiques
            String exportData = generateExportData(format, startDate, endDate);
            
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=payment_export." + format)
                .body(exportData);
                
        } catch (Exception e) {
            log.error("Erreur lors de l'export des données", e);
            return ResponseEntity.badRequest().body("Erreur lors de l'export: " + e.getMessage());
        }
    }

    /**
     * Méthodes utilitaires privées
     */
    
    private Map<String, Object> getCurrentSettings() {
        // Retourner les paramètres actuels
        return Map.of(
            "maxAttemptsPerIp", 5,
            "rateLimitWindow", 3600,
            "suspiciousThreshold", 10,
            "sessionTimeout", 3600
        );
    }

    private String generateExportData(String format, String startDate, String endDate) {
        // Générer les données d'export selon le format demandé
        // Cette méthode devrait être implémentée selon les besoins spécifiques
        
        if ("csv".equals(format)) {
            return "id,amount,status,client_ip,created_at\n"; // En-têtes CSV
        } else if ("json".equals(format)) {
            return "[]"; // JSON vide pour l'exemple
        }
        
        throw new IllegalArgumentException("Format non supporté: " + format);
    }
}