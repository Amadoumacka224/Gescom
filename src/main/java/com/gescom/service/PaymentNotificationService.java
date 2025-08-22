package com.gescom.service;

import com.gescom.entity.ExternalPayment;
import com.gescom.entity.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service de notification pour les paiements externes
 * Gestion des emails de confirmation et d'alerte
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@gescom.fr}")
    private String fromEmail;

    @Value("${notification.payment.admin-email:admin@gescom.fr}")
    private String adminEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRENCH);

    /**
     * Envoie une confirmation de paiement réussi au client
     */
    public void sendPaymentConfirmation(ExternalPayment payment) {
        try {
            log.info("Envoi de confirmation de paiement pour: {}", payment.getId());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "GESCOM - Paiements");
            helper.setTo(payment.getCustomerEmail());
            helper.setSubject("Confirmation de paiement - Facture " + payment.getInvoice().getInvoiceNumber());

            // Génération du contenu HTML
            Context context = new Context(Locale.FRENCH);
            context.setVariable("payment", payment);
            context.setVariable("invoice", payment.getInvoice());
            context.setVariable("customerName", payment.getCustomerName());
            context.setVariable("paymentDate", payment.getCompletedAt().format(DATE_FORMATTER));
            context.setVariable("baseUrl", baseUrl);

            String htmlContent = templateEngine.process("email/payment-confirmation", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            
            log.info("Confirmation de paiement envoyée avec succès à: {}", payment.getCustomerEmail());

        } catch (MailException | MessagingException e) {
            log.error("Erreur lors de l'envoi de la confirmation de paiement pour: {}", payment.getId(), e);
            // Ne pas lever d'exception pour ne pas bloquer le processus de paiement
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Envoie une notification d'échec de paiement au client
     */
    public void sendPaymentFailureNotification(ExternalPayment payment) {
        try {
            log.info("Envoi de notification d'échec de paiement pour: {}", payment.getId());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(payment.getCustomerEmail());
            message.setSubject("Échec de paiement - Facture " + payment.getInvoice().getInvoiceNumber());

            StringBuilder text = new StringBuilder();
            text.append("Bonjour ").append(payment.getCustomerName()).append(",\n\n");
            text.append("Nous vous informons que votre tentative de paiement pour la facture ");
            text.append(payment.getInvoice().getInvoiceNumber()).append(" a échoué.\n\n");
            text.append("Montant: ").append(payment.getAmount()).append(" €\n");
            text.append("Raison: ").append(payment.getFailureReason() != null ? payment.getFailureReason() : "Erreur technique").append("\n\n");
            text.append("Vous pouvez réessayer votre paiement en ligne à l'adresse suivante:\n");
            text.append(baseUrl).append("/payment\n\n");
            text.append("Pour toute question, contactez notre service client:\n");
            text.append("- Téléphone: 01 23 45 67 89\n");
            text.append("- Email: support@gescom.fr\n\n");
            text.append("Cordialement,\n");
            text.append("L'équipe GESCOM");

            message.setText(text.toString());
            mailSender.send(message);

            log.info("Notification d'échec envoyée avec succès à: {}", payment.getCustomerEmail());

        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de la notification d'échec pour: {}", payment.getId(), e);
        }
    }

    /**
     * Envoie une alerte de sécurité aux administrateurs
     */
    public void sendSecurityAlert(String alertType, String details, String clientIp) {
        try {
            log.warn("Envoi d'alerte de sécurité: {} depuis IP: {}", alertType, clientIp);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject("[ALERTE SÉCURITÉ] " + alertType + " - GESCOM Paiements");

            StringBuilder text = new StringBuilder();
            text.append("ALERTE DE SÉCURITÉ DÉTECTÉE\n");
            text.append("==========================\n\n");
            text.append("Type d'alerte: ").append(alertType).append("\n");
            text.append("Adresse IP: ").append(clientIp).append("\n");
            text.append("Date/Heure: ").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\n\n");
            text.append("Détails:\n").append(details).append("\n\n");
            text.append("Actions recommandées:\n");
            text.append("- Vérifier les logs de sécurité\n");
            text.append("- Analyser l'activité de cette IP\n");
            text.append("- Bloquer l'IP si nécessaire\n\n");
            text.append("URL d'administration: ").append(baseUrl).append("/admin/security\n\n");
            text.append("Système de monitoring GESCOM");

            message.setText(text.toString());
            mailSender.send(message);

            log.info("Alerte de sécurité envoyée aux administrateurs");

        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de l'alerte de sécurité", e);
        }
    }

    /**
     * Envoie un rapport quotidien de sécurité
     */
    public void sendDailySecurityReport(SecurityReportData reportData) {
        try {
            log.info("Envoi du rapport quotidien de sécurité");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "GESCOM - Monitoring");
            helper.setTo(adminEmail);
            helper.setSubject("Rapport quotidien de sécurité - Paiements GESCOM");

            // Génération du contenu HTML
            Context context = new Context(Locale.FRENCH);
            context.setVariable("reportData", reportData);
            context.setVariable("reportDate", java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            context.setVariable("baseUrl", baseUrl);

            String htmlContent = templateEngine.process("email/security-report", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            
            log.info("Rapport quotidien de sécurité envoyé avec succès");

        } catch (MailException | MessagingException e) {
            log.error("Erreur lors de l'envoi du rapport quotidien de sécurité", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Envoie une notification de facture payée à l'administrateur
     */
    public void sendInvoicePaidNotification(Invoice invoice, ExternalPayment payment) {
        try {
            log.info("Envoi de notification de facture payée: {}", invoice.getInvoiceNumber());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject("Facture payée en ligne - " + invoice.getInvoiceNumber());

            StringBuilder text = new StringBuilder();
            text.append("Une facture a été payée en ligne:\n\n");
            text.append("Facture: ").append(invoice.getInvoiceNumber()).append("\n");
            text.append("Montant: ").append(payment.getAmount()).append(" €\n");
            text.append("Client: ").append(payment.getCustomerName()).append("\n");
            text.append("Email: ").append(payment.getCustomerEmail()).append("\n");
            text.append("Mode de paiement: ").append(payment.getPaymentMethod().getDisplayName()).append("\n");
            text.append("Date de paiement: ").append(payment.getCompletedAt().format(DATE_FORMATTER)).append("\n");
            text.append("Référence transaction: ").append(payment.getGatewayTransactionId()).append("\n\n");
            text.append("La facture a été automatiquement mise à jour dans le système.\n\n");
            text.append("Voir la facture: ").append(baseUrl).append("/invoices/").append(invoice.getId());

            message.setText(text.toString());
            mailSender.send(message);

            log.info("Notification de facture payée envoyée aux administrateurs");

        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de la notification de facture payée", e);
        }
    }

    /**
     * Teste la configuration email
     */
    public boolean testEmailConfiguration() {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject("Test de configuration email - GESCOM");
            message.setText("Ceci est un test de la configuration email pour le système de paiement GESCOM.\n\n" +
                          "Si vous recevez ce message, la configuration est correcte.\n\n" +
                          "Date: " + java.time.LocalDateTime.now().format(DATE_FORMATTER));

            mailSender.send(message);
            log.info("Email de test envoyé avec succès");
            return true;

        } catch (MailException e) {
            log.error("Échec du test de configuration email", e);
            return false;
        }
    }

    /**
     * Classe pour les données du rapport de sécurité
     */
    public static class SecurityReportData {
        private final int totalPayments;
        private final int successfulPayments;
        private final int failedPayments;
        private final int suspiciousIps;
        private final int blockedAttempts;
        private final double successRate;

        public SecurityReportData(int totalPayments, int successfulPayments, int failedPayments, 
                                int suspiciousIps, int blockedAttempts) {
            this.totalPayments = totalPayments;
            this.successfulPayments = successfulPayments;
            this.failedPayments = failedPayments;
            this.suspiciousIps = suspiciousIps;
            this.blockedAttempts = blockedAttempts;
            this.successRate = totalPayments > 0 ? (double) successfulPayments / totalPayments * 100 : 0;
        }

        // Getters
        public int getTotalPayments() { return totalPayments; }
        public int getSuccessfulPayments() { return successfulPayments; }
        public int getFailedPayments() { return failedPayments; }
        public int getSuspiciousIps() { return suspiciousIps; }
        public int getBlockedAttempts() { return blockedAttempts; }
        public double getSuccessRate() { return successRate; }
    }
}