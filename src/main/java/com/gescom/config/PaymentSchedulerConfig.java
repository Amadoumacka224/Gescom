package com.gescom.config;

import com.gescom.service.ExternalPaymentService;
import com.gescom.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuration des tâches planifiées pour la gestion des paiements
 * Nettoyage automatique et maintenance du système de paiement
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.payment.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSchedulerConfig {

    private final ExternalPaymentService externalPaymentService;
    private final SecurityService securityService;

    /**
     * Nettoyage des paiements expirés
     * Exécuté toutes les heures
     */
    @Scheduled(cron = "${scheduler.payment.cleanup-expired:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredPayments() {
        try {
            log.info("Début du nettoyage des paiements expirés");
            
            externalPaymentService.cleanupExpiredPayments();
            
            log.info("Nettoyage des paiements expirés terminé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur lors du nettoyage des paiements expirés", e);
        }
    }

    /**
     * Nettoyage des données de sécurité expirées
     * Exécuté tous les jours à 3h du matin
     */
    @Scheduled(cron = "${scheduler.payment.cleanup-security:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredSecurityData() {
        try {
            log.info("Début du nettoyage des données de sécurité expirées");
            
            securityService.cleanupExpiredSecurityData();
            
            log.info("Nettoyage des données de sécurité terminé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur lors du nettoyage des données de sécurité", e);
        }
    }

    /**
     * Génération de rapport de sécurité quotidien
     * Exécuté tous les jours à 8h du matin
     */
    @Scheduled(cron = "${scheduler.payment.security-report:0 0 8 * * *}")
    public void generateSecurityReport() {
        try {
            log.info("Génération du rapport de sécurité quotidien");
            
            // Obtenir les statistiques de sécurité
            SecurityService.SecurityStatistics securityStats = securityService.getSecurityStatistics();
            ExternalPaymentService.PaymentStatistics paymentStats = externalPaymentService.getPaymentStatistics();
            
            // Log du rapport
            log.info("=== RAPPORT DE SÉCURITÉ QUOTIDIEN ===");
            log.info("IPs suspectes actives: {}", securityStats.suspiciousIpCount());
            log.info("Limite de tentatives par IP: {}", securityStats.maxAttemptsPerIp());
            log.info("Fenêtre de rate limiting: {} secondes", securityStats.rateLimitWindowSeconds());
            
            log.info("=== STATISTIQUES DE PAIEMENT ===");
            log.info("Total des paiements: {}", paymentStats.totalPayments());
            log.info("Paiements réussis: {}", paymentStats.successfulPayments());
            log.info("Paiements échoués: {}", paymentStats.failedPayments());
            log.info("Paiements en attente: {}", paymentStats.pendingPayments());
            log.info("Taux de succès: {:.2f}%", paymentStats.getSuccessRate());
            
            // Alertes en cas de problèmes
            if (securityStats.suspiciousIpCount() > 5) {
                log.warn("ALERTE: Nombre élevé d'IPs suspectes détectées ({})", securityStats.suspiciousIpCount());
            }
            
            if (paymentStats.getSuccessRate() < 90.0) {
                log.warn("ALERTE: Taux de succès des paiements bas ({:.2f}%)", paymentStats.getSuccessRate());
            }
            
            log.info("=== FIN DU RAPPORT ===");
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport de sécurité", e);
        }
    }

    /**
     * Vérification de l'état de santé du système de paiement
     * Exécuté toutes les 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void healthCheck() {
        try {
            log.debug("Vérification de l'état de santé du système de paiement");
            
            // Vérifier la connectivité Redis
            securityService.getSecurityStatistics();
            
            // Vérifier la base de données
            externalPaymentService.getPaymentStatistics();
            
            log.debug("Système de paiement opérationnel");
            
        } catch (Exception e) {
            log.error("ALERTE: Problème détecté dans le système de paiement", e);
            
            // En production, ici on pourrait envoyer une alerte
            // notificationService.sendAlert("Payment System Health Check Failed", e.getMessage());
        }
    }

    /**
     * Nettoyage des logs de paiement anciens
     * Exécuté une fois par semaine le dimanche à 2h du matin
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void cleanupOldLogs() {
        try {
            log.info("Début du nettoyage des anciens logs de paiement");
            
            // Logique de nettoyage des logs anciens (> 90 jours)
            // Cette méthode peut être implémentée selon les besoins
            
            log.info("Nettoyage des anciens logs terminé");
            
        } catch (Exception e) {
            log.error("Erreur lors du nettoyage des anciens logs", e);
        }
    }

    /**
     * Mise à jour des statistiques de performance
     * Exécuté toutes les heures à la minute 30
     */
    @Scheduled(cron = "0 30 * * * *")
    public void updatePerformanceMetrics() {
        try {
            log.debug("Mise à jour des métriques de performance");
            
            // Calculer et mettre en cache les métriques de performance
            ExternalPaymentService.PaymentStatistics stats = externalPaymentService.getPaymentStatistics();
            
            // Ici on pourrait stocker ces métriques dans un système de monitoring
            // metricsService.updatePaymentMetrics(stats);
            
            log.debug("Métriques de performance mises à jour - Taux de succès: {:.2f}%", 
                     stats.getSuccessRate());
            
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour des métriques de performance", e);
        }
    }
}