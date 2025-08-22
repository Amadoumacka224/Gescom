package com.gescom.service;

import com.gescom.entity.Invoice;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private InvoicePdfService pdfService;

    @Value("${app.mail.from:noreply@gescom.com}")
    private String fromEmail;

    @Value("${app.company.name:Votre Entreprise}")
    private String companyName;

    public void sendInvoiceEmail(Invoice invoice, String recipientEmail, boolean attachPdf) throws MessagingException, IOException {
        logger.info("Envoi de la facture {} par email à {}", invoice.getInvoiceNumber(), recipientEmail);
        
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        // Configuration de base
        helper.setFrom(fromEmail);
        helper.setTo(recipientEmail);
        helper.setSubject(generateSubject(invoice));
        
        // Corps du message
        String htmlContent = generateEmailContent(invoice);
        helper.setText(htmlContent, true);
        
        // Pièce jointe PDF
        if (attachPdf) {
            try {
                byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);
                String filename = "Facture_" + invoice.getInvoiceNumber() + ".pdf";
                helper.addAttachment(filename, new ByteArrayResource(pdfBytes));
                logger.info("PDF attaché à l'email: {}", filename);
            } catch (Exception e) {
                logger.error("Erreur lors de la génération du PDF pour l'email", e);
                throw new IOException("Impossible de générer le PDF pour l'email", e);
            }
        }
        
        // Envoi
        emailSender.send(message);
        logger.info("Email envoyé avec succès pour la facture {}", invoice.getInvoiceNumber());
    }

    public void sendInvoiceReminder(Invoice invoice, String recipientEmail) throws MessagingException, IOException {
        logger.info("Envoi de relance pour la facture {} à {}", invoice.getInvoiceNumber(), recipientEmail);
        
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail);
        helper.setTo(recipientEmail);
        helper.setSubject("Relance - " + generateSubject(invoice));
        
        String htmlContent = generateReminderEmailContent(invoice);
        helper.setText(htmlContent, true);
        
        // Toujours attacher le PDF pour les relances
        try {
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);
            String filename = "Facture_" + invoice.getInvoiceNumber() + "_Relance.pdf";
            helper.addAttachment(filename, new ByteArrayResource(pdfBytes));
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du PDF pour la relance", e);
            throw new IOException("Impossible de générer le PDF pour la relance", e);
        }
        
        emailSender.send(message);
        logger.info("Relance envoyée avec succès pour la facture {}", invoice.getInvoiceNumber());
    }

    private String generateSubject(Invoice invoice) {
        String typeLabel = invoice.getInvoiceType() != null ? 
            invoice.getInvoiceType().getDisplayName() : "Facture";
        return String.format("%s %s - %s", typeLabel, invoice.getInvoiceNumber(), companyName);
    }

    private String generateEmailContent(Invoice invoice) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".header { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin-bottom: 20px; }");
        html.append(".invoice-details { background-color: #fff; border: 1px solid #ddd; padding: 15px; border-radius: 5px; margin: 20px 0; }");
        html.append(".footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }");
        html.append(".amount { font-size: 18px; font-weight: bold; color: #28a745; }");
        html.append(".overdue { color: #dc3545; font-weight: bold; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        
        // En-tête
        html.append("<div class='header'>");
        html.append("<h2>").append(companyName).append("</h2>");
        String clientName = (invoice.getOrder() != null && invoice.getOrder().getClient() != null) ? 
            invoice.getOrder().getClient().getName() : "Cher client";
        html.append("<p>Bonjour ").append(clientName).append(",</p>");
        html.append("</div>");
        
        // Corps principal
        if (invoice.getInvoiceType() != null && invoice.getInvoiceType().name().equals("CREDIT_NOTE")) {
            html.append("<p>Vous trouverez ci-joint votre avoir <strong>").append(invoice.getInvoiceNumber()).append("</strong>.</p>");
        } else {
            html.append("<p>Vous trouverez ci-joint votre facture <strong>").append(invoice.getInvoiceNumber()).append("</strong>.</p>");
        }
        
        // Détails de la facture
        html.append("<div class='invoice-details'>");
        html.append("<h3>Détails de la facture</h3>");
        html.append("<table style='width: 100%; border-collapse: collapse;'>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Numéro:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;'>").append(invoice.getInvoiceNumber()).append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Date:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;'>").append(invoice.getInvoiceDate().format(DATE_FORMATTER)).append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Échéance:</strong></td>");
        String dueDateClass = invoice.isOverdue() ? "overdue" : "";
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;' class='").append(dueDateClass).append("'>")
            .append(invoice.getDueDate().format(DATE_FORMATTER));
        if (invoice.isOverdue()) {
            html.append(" (En retard de ").append(invoice.getDaysOverdue()).append(" jour(s))");
        }
        html.append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Montant total:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;' class='amount'>")
            .append(formatCurrency(invoice.getTotalAmount())).append("</td></tr>");
        
        if (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Déjà payé:</strong></td>");
            html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;'>")
                .append(formatCurrency(invoice.getPaidAmount())).append("</td></tr>");
            
            html.append("<tr><td style='padding: 5px;'><strong>Reste à payer:</strong></td>");
            html.append("<td style='padding: 5px;' class='amount'>")
                .append(formatCurrency(invoice.getRemainingAmount())).append("</td></tr>");
        }
        
        html.append("</table>");
        html.append("</div>");
        
        // Instructions de paiement
        if (!invoice.getStatus().name().equals("PAID")) {
            html.append("<div class='invoice-details'>");
            html.append("<h3>Informations de paiement</h3>");
            html.append("<p>Merci de procéder au règlement avant la date d'échéance.</p>");
            html.append("<p><strong>Modalités de paiement :</strong></p>");
            html.append("<ul>");
            html.append("<li>Virement bancaire</li>");
            html.append("<li>Chèque à l'ordre de ").append(companyName).append("</li>");
            html.append("</ul>");
            html.append("<p><em>Merci de mentionner le numéro de facture ").append(invoice.getInvoiceNumber())
                .append(" lors de votre paiement.</em></p>");
            html.append("</div>");
        }
        
        // Commande associée
        if (invoice.getOrder() != null) {
            html.append("<p><small>Cette facture correspond à la commande n° <strong>")
                .append(invoice.getOrder().getOrderNumber()).append("</strong> du ")
                .append(invoice.getOrder().getOrderDate().format(DATE_FORMATTER)).append(".</small></p>");
        }
        
        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>Cordialement,<br>");
        html.append("L'équipe ").append(companyName).append("</p>");
        html.append("<hr>");
        html.append("<p><small>");
        html.append("Cet email a été envoyé automatiquement le ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")));
        html.append("<br>Pour toute question, n'hésitez pas à nous contacter.");
        html.append("</small></p>");
        html.append("</div>");
        
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    private String generateReminderEmailContent(Invoice invoice) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".header { background-color: #fff3cd; padding: 20px; border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #ffc107; }");
        html.append(".invoice-details { background-color: #fff; border: 1px solid #ddd; padding: 15px; border-radius: 5px; margin: 20px 0; }");
        html.append(".footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }");
        html.append(".amount { font-size: 18px; font-weight: bold; color: #dc3545; }");
        html.append(".overdue { color: #dc3545; font-weight: bold; }");
        html.append(".urgent { background-color: #f8d7da; border: 1px solid #f5c6cb; padding: 15px; border-radius: 5px; margin: 20px 0; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        
        // En-tête de relance
        html.append("<div class='header'>");
        html.append("<h2>⚠️ Relance - ").append(companyName).append("</h2>");
        String clientName = (invoice.getOrder() != null && invoice.getOrder().getClient() != null) ? 
            invoice.getOrder().getClient().getName() : "Cher client";
        html.append("<p>Bonjour ").append(clientName).append(",</p>");
        html.append("</div>");
        
        // Message de relance
        if (invoice.isOverdue()) {
            html.append("<div class='urgent'>");
            html.append("<h3>🚨 Facture en retard</h3>");
            html.append("<p>Nous constatons que votre facture <strong>").append(invoice.getInvoiceNumber())
                .append("</strong> n'a toujours pas été réglée alors qu'elle était échue le ")
                .append(invoice.getDueDate().format(DATE_FORMATTER)).append(" (soit il y a ")
                .append(invoice.getDaysOverdue()).append(" jour(s)).</p>");
            html.append("</div>");
        } else {
            html.append("<p>Nous vous rappelons que votre facture <strong>").append(invoice.getInvoiceNumber())
                .append("</strong> arrive à échéance le ").append(invoice.getDueDate().format(DATE_FORMATTER)).append(".</p>");
        }
        
        // Détails de la facture (réutilisation du code précédent)
        html.append("<div class='invoice-details'>");
        html.append("<h3>Rappel des détails</h3>");
        html.append("<table style='width: 100%; border-collapse: collapse;'>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Numéro:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;'>").append(invoice.getInvoiceNumber()).append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Date de facturation:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee;'>").append(invoice.getInvoiceDate().format(DATE_FORMATTER)).append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px; border-bottom: 1px solid #eee;'><strong>Date d'échéance:</strong></td>");
        html.append("<td style='padding: 5px; border-bottom: 1px solid #eee; color: #dc3545; font-weight: bold;'>")
            .append(invoice.getDueDate().format(DATE_FORMATTER)).append("</td></tr>");
        
        html.append("<tr><td style='padding: 5px;'><strong>Montant à régler:</strong></td>");
        html.append("<td style='padding: 5px;' class='amount'>")
            .append(formatCurrency(invoice.getRemainingAmount())).append("</td></tr>");
        
        html.append("</table>");
        html.append("</div>");
        
        // Action demandée
        html.append("<div class='invoice-details'>");
        html.append("<h3>Action requise</h3>");
        html.append("<p><strong>Merci de procéder au règlement dans les plus brefs délais.</strong></p>");
        html.append("<p>Si vous avez déjà effectué ce paiement, merci de nous faire parvenir une copie du justificatif.</p>");
        html.append("<p>Pour toute difficulté de paiement, n'hésitez pas à nous contacter pour étudier ensemble une solution.</p>");
        html.append("</div>");
        
        // Commande associée
        if (invoice.getOrder() != null) {
            html.append("<p><small>Cette facture correspond à la commande n° <strong>")
                .append(invoice.getOrder().getOrderNumber()).append("</strong> du ")
                .append(invoice.getOrder().getOrderDate().format(DATE_FORMATTER)).append(".</small></p>");
        }
        
        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>Cordialement,<br>");
        html.append("L'équipe ").append(companyName).append("</p>");
        html.append("<hr>");
        html.append("<p><small>");
        html.append("Relance automatique envoyée le ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")));
        html.append("<br>Pour toute question, n'hésitez pas à nous contacter rapidement.");
        html.append("</small></p>");
        html.append("</div>");
        
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    private String formatCurrency(java.math.BigDecimal amount) {
        if (amount == null) return "0,00 €";
        return String.format("%.2f €", amount.doubleValue()).replace('.', ',');
    }
}