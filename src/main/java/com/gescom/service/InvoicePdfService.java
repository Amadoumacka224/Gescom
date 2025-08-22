package com.gescom.service;

import com.gescom.entity.Invoice;
import com.gescom.entity.InvoiceItem;
import com.gescom.entity.OrderItem;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InvoicePdfService {

    private static final Logger logger = LoggerFactory.getLogger(InvoicePdfService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generateInvoicePdf(Invoice invoice) throws IOException {
        logger.info("Génération du PDF pour la facture {}", invoice.getInvoiceNumber());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        try {
            PdfFont font = PdfFontFactory.createFont();
            PdfFont boldFont = PdfFontFactory.createFont();
            
            // En-tête de la facture
            addHeader(document, invoice, boldFont);
            
            // Informations entreprise et client
            addCompanyAndClientInfo(document, invoice, font, boldFont);
            
            // Articles
            addItemsTable(document, invoice, font, boldFont);
            
            // Totaux
            addTotalsSection(document, invoice, font, boldFont);
            
            // Notes et conditions
            addNotesAndTerms(document, invoice, font, boldFont);
            
            // Pied de page
            addFooter(document, font);
            
        } finally {
            document.close();
        }
        
        logger.info("PDF généré avec succès pour la facture {}", invoice.getInvoiceNumber());
        return outputStream.toByteArray();
    }

    private void addHeader(Document document, Invoice invoice, PdfFont boldFont) {
        // Titre principal
        Paragraph title = new Paragraph("FACTURE")
                .setFont(boldFont)
                .setFontSize(24)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(title);
        
        // Table pour les informations de base
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        headerTable.setWidth(UnitValue.createPercentValue(100));
        
        // Colonne gauche - Logo/Entreprise
        Cell leftCell = new Cell();
        leftCell.add(new Paragraph("VOTRE ENTREPRISE").setFont(boldFont).setFontSize(14));
        leftCell.add(new Paragraph("123 Rue de l'Exemple\n75000 Paris, France\nTél: +33 1 23 45 67 89\nEmail: contact@votresite.com"));
        leftCell.setBorder(null);
        
        // Colonne droite - Informations facture
        Cell rightCell = new Cell();
        rightCell.add(new Paragraph("Numéro: " + invoice.getInvoiceNumber()).setFont(boldFont));
        rightCell.add(new Paragraph("Date: " + invoice.getInvoiceDate().format(DATE_FORMATTER)));
        rightCell.add(new Paragraph("Échéance: " + invoice.getDueDate().format(DATE_FORMATTER)));
        rightCell.add(new Paragraph("Statut: " + invoice.getStatus().getDisplayName()).setFont(boldFont));
        if (invoice.getInvoiceType() != null) {
            rightCell.add(new Paragraph("Type: " + invoice.getInvoiceType().getDisplayName()));
        }
        rightCell.setBorder(null);
        rightCell.setTextAlignment(TextAlignment.RIGHT);
        
        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        document.add(headerTable);
        
        document.add(new Paragraph("\n"));
    }

    private void addCompanyAndClientInfo(Document document, Invoice invoice, PdfFont font, PdfFont boldFont) {
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        infoTable.setWidth(UnitValue.createPercentValue(100));
        
        // Informations client
        Cell clientCell = new Cell();
        clientCell.add(new Paragraph("FACTURER À:").setFont(boldFont).setFontSize(12));
        
        if (invoice.getOrder() != null && invoice.getOrder().getClient() != null) {
            var client = invoice.getOrder().getClient();
            clientCell.add(new Paragraph(client.getName()).setFont(boldFont));
            if (client.getCompanyName() != null && !client.getCompanyName().isEmpty()) {
                clientCell.add(new Paragraph(client.getCompanyName()));
            }
            clientCell.add(new Paragraph(client.getEmail()));
            if (client.getPhoneNumber() != null && !client.getPhoneNumber().isEmpty()) {
                clientCell.add(new Paragraph("Tél: " + client.getPhoneNumber()));
            }
        }
        
        if (invoice.getBillingAddress() != null && !invoice.getBillingAddress().isEmpty()) {
            clientCell.add(new Paragraph("\nAdresse de facturation:").setFont(boldFont).setFontSize(10));
            clientCell.add(new Paragraph(invoice.getBillingAddress()).setFontSize(10));
        }
        clientCell.setBorder(null);
        
        // Informations commande (si disponible)
        Cell orderCell = new Cell();
        if (invoice.getOrder() != null) {
            orderCell.add(new Paragraph("COMMANDE ASSOCIÉE:").setFont(boldFont).setFontSize(12));
            orderCell.add(new Paragraph("N° Commande: " + invoice.getOrder().getOrderNumber()));
            orderCell.add(new Paragraph("Date commande: " + invoice.getOrder().getOrderDate().format(DATE_FORMATTER)));
            orderCell.add(new Paragraph("Statut commande: " + invoice.getOrder().getStatus().getDisplayName()));
        }
        orderCell.setBorder(null);
        orderCell.setTextAlignment(TextAlignment.RIGHT);
        
        infoTable.addCell(clientCell);
        infoTable.addCell(orderCell);
        document.add(infoTable);
        
        document.add(new Paragraph("\n"));
    }

    private void addItemsTable(Document document, Invoice invoice, PdfFont font, PdfFont boldFont) {
        // En-tête de la section
        document.add(new Paragraph("DÉTAIL DES ARTICLES").setFont(boldFont).setFontSize(14).setMarginBottom(10));
        
        // Table des articles
        Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{40, 10, 15, 10, 12.5F, 12.5F}));
        itemsTable.setWidth(UnitValue.createPercentValue(100));
        
        // En-têtes de colonnes
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("Description").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("Qté").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER));
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("Prix unitaire").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.RIGHT));
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("TVA").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER));
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("Total HT").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.RIGHT));
        itemsTable.addHeaderCell(new Cell().add(new Paragraph("Total TTC").setFont(boldFont)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.RIGHT));
        
        // Articles de la facture (priorité) ou de la commande
        if (invoice.getInvoiceItems() != null && !invoice.getInvoiceItems().isEmpty()) {
            // Articles spécifiques à la facture
            for (InvoiceItem item : invoice.getInvoiceItems()) {
                addItemRow(itemsTable, item.getDescription(), item.getQuantity(), 
                          item.getUnitPrice(), item.getVatRate(), 
                          item.getTotalPriceHT(), item.getTotalPrice(),
                          item.getReference());
            }
        } else if (invoice.getOrder() != null && invoice.getOrder().getOrderItems() != null) {
            // Articles de la commande
            for (OrderItem item : invoice.getOrder().getOrderItems()) {
                String description = item.getProduct() != null ? item.getProduct().getName() : item.getDescription();
                String reference = item.getProduct() != null ? item.getProduct().getReference() : null;
                addItemRow(itemsTable, description, item.getQuantity(),
                          item.getUnitPrice(), item.getVatRate(),
                          item.getTotalPriceHT(), item.getTotalPrice(),
                          reference);
            }
        }
        
        document.add(itemsTable);
        document.add(new Paragraph("\n"));
    }

    private void addItemRow(Table table, String description, Integer quantity, 
                           BigDecimal unitPrice, BigDecimal vatRate,
                           BigDecimal totalHT, BigDecimal totalTTC, String reference) {
        
        // Description avec référence si disponible
        Paragraph descParagraph = new Paragraph(description);
        if (reference != null && !reference.isEmpty()) {
            descParagraph.add(new Paragraph("\nRéf: " + reference).setFontSize(8).setFontColor(ColorConstants.GRAY));
        }
        table.addCell(new Cell().add(descParagraph));
        
        table.addCell(new Cell().add(new Paragraph(String.valueOf(quantity))).setTextAlignment(TextAlignment.CENTER));
        table.addCell(new Cell().add(new Paragraph(formatCurrency(unitPrice))).setTextAlignment(TextAlignment.RIGHT));
        table.addCell(new Cell().add(new Paragraph(formatPercentage(vatRate))).setTextAlignment(TextAlignment.CENTER));
        table.addCell(new Cell().add(new Paragraph(formatCurrency(totalHT))).setTextAlignment(TextAlignment.RIGHT));
        table.addCell(new Cell().add(new Paragraph(formatCurrency(totalTTC))).setTextAlignment(TextAlignment.RIGHT));
    }

    private void addTotalsSection(Document document, Invoice invoice, PdfFont font, PdfFont boldFont) {
        // Table pour les totaux (alignée à droite)
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{60, 40}));
        totalsTable.setWidth(UnitValue.createPercentValue(100));
        
        // Colonne vide pour pousser les totaux à droite
        Cell emptyCell = new Cell();
        emptyCell.setBorder(null);
        
        // Colonne avec les totaux
        Cell totalsCell = new Cell();
        totalsCell.setBorder(null);
        
        // Sous-total HT
        Table innerTotalsTable = new Table(UnitValue.createPercentArray(new float[]{70, 30}));
        innerTotalsTable.setWidth(UnitValue.createPercentValue(100));
        
        innerTotalsTable.addCell(new Cell().add(new Paragraph("Sous-total HT:")).setBorder(null));
        innerTotalsTable.addCell(new Cell().add(new Paragraph(formatCurrency(invoice.getTotalAmountHT()))).setBorder(null).setTextAlignment(TextAlignment.RIGHT));
        
        // Remise si applicable
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            innerTotalsTable.addCell(new Cell().add(new Paragraph("Remise (" + formatPercentage(invoice.getDiscountRate()) + "):")).setBorder(null).setFontColor(ColorConstants.RED));
            innerTotalsTable.addCell(new Cell().add(new Paragraph("-" + formatCurrency(invoice.getDiscountAmount()))).setBorder(null).setTextAlignment(TextAlignment.RIGHT).setFontColor(ColorConstants.RED));
        }
        
        // Frais de port si applicable
        if (invoice.getShippingCost() != null && invoice.getShippingCost().compareTo(BigDecimal.ZERO) > 0) {
            innerTotalsTable.addCell(new Cell().add(new Paragraph("Frais de port:")).setBorder(null));
            innerTotalsTable.addCell(new Cell().add(new Paragraph(formatCurrency(invoice.getShippingCost()))).setBorder(null).setTextAlignment(TextAlignment.RIGHT));
        }
        
        // TVA
        innerTotalsTable.addCell(new Cell().add(new Paragraph("TVA:")).setBorder(null));
        innerTotalsTable.addCell(new Cell().add(new Paragraph(formatCurrency(invoice.getTotalVatAmount()))).setBorder(null).setTextAlignment(TextAlignment.RIGHT));
        
        // Ligne de séparation
        Cell separatorLeft = new Cell().setBorder(null).setBorderTop(new com.itextpdf.layout.borders.SolidBorder(1f));
        Cell separatorRight = new Cell().setBorder(null).setBorderTop(new com.itextpdf.layout.borders.SolidBorder(1f));
        innerTotalsTable.addCell(separatorLeft);
        innerTotalsTable.addCell(separatorRight);
        
        // Total TTC
        innerTotalsTable.addCell(new Cell().add(new Paragraph("TOTAL TTC:").setFont(boldFont).setFontSize(12)).setBorder(null));
        innerTotalsTable.addCell(new Cell().add(new Paragraph(formatCurrency(invoice.getTotalAmount())).setFont(boldFont).setFontSize(12)).setBorder(null).setTextAlignment(TextAlignment.RIGHT));
        
        totalsCell.add(innerTotalsTable);
        
        totalsTable.addCell(emptyCell);
        totalsTable.addCell(totalsCell);
        document.add(totalsTable);
        
        // Informations de paiement si disponibles
        if (invoice.getPaymentDate() != null || invoice.getPaidAmount() != null) {
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("INFORMATIONS DE PAIEMENT").setFont(boldFont).setFontSize(12));
            
            Table paymentTable = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
            paymentTable.setWidth(UnitValue.createPercentValue(100));
            
            if (invoice.getPaymentDate() != null) {
                paymentTable.addCell(new Cell().add(new Paragraph("Date de paiement:").setFont(boldFont)).setBorder(null));
                paymentTable.addCell(new Cell().add(new Paragraph(invoice.getPaymentDate().format(DATE_FORMATTER))).setBorder(null));
            }
            
            if (invoice.getPaymentMethod() != null) {
                paymentTable.addCell(new Cell().add(new Paragraph("Méthode:").setFont(boldFont)).setBorder(null));
                paymentTable.addCell(new Cell().add(new Paragraph(invoice.getPaymentMethod().getDisplayName())).setBorder(null));
            }
            
            if (invoice.getPaidAmount() != null) {
                paymentTable.addCell(new Cell().add(new Paragraph("Montant payé:").setFont(boldFont)).setBorder(null));
                paymentTable.addCell(new Cell().add(new Paragraph(formatCurrency(invoice.getPaidAmount()))).setBorder(null));
                
                BigDecimal remaining = invoice.getRemainingAmount();
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    paymentTable.addCell(new Cell().add(new Paragraph("Reste à payer:").setFont(boldFont)).setBorder(null));
                    paymentTable.addCell(new Cell().add(new Paragraph(formatCurrency(remaining)).setFontColor(ColorConstants.RED)).setBorder(null));
                }
            }
            
            if (invoice.getPaymentReference() != null && !invoice.getPaymentReference().isEmpty()) {
                paymentTable.addCell(new Cell().add(new Paragraph("Référence:").setFont(boldFont)).setBorder(null));
                paymentTable.addCell(new Cell().add(new Paragraph(invoice.getPaymentReference())).setBorder(null));
            }
            
            document.add(paymentTable);
        }
        
        document.add(new Paragraph("\n"));
    }

    private void addNotesAndTerms(Document document, Invoice invoice, PdfFont font, PdfFont boldFont) {
        // Notes
        if (invoice.getNotes() != null && !invoice.getNotes().isEmpty()) {
            document.add(new Paragraph("NOTES:").setFont(boldFont).setFontSize(12));
            document.add(new Paragraph(invoice.getNotes()).setFontSize(10).setMarginBottom(10));
        }
        
        // Conditions générales
        if (invoice.getTermsConditions() != null && !invoice.getTermsConditions().isEmpty()) {
            document.add(new Paragraph("CONDITIONS GÉNÉRALES:").setFont(boldFont).setFontSize(12));
            document.add(new Paragraph(invoice.getTermsConditions()).setFontSize(10).setMarginBottom(10));
        } else {
            // Conditions par défaut
            document.add(new Paragraph("CONDITIONS GÉNÉRALES:").setFont(boldFont).setFontSize(12));
            document.add(new Paragraph("Paiement à 30 jours. Tout retard de paiement donnera lieu à l'application d'une pénalité de 3% par mois de retard.")
                    .setFontSize(10).setMarginBottom(10));
        }
    }

    private void addFooter(Document document, PdfFont font) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Merci pour votre confiance !")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setFontColor(ColorConstants.GRAY));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0,00 €";
        return String.format("%.2f €", amount.doubleValue()).replace('.', ',');
    }

    private String formatPercentage(BigDecimal percentage) {
        if (percentage == null) return "0%";
        return String.format("%.1f%%", percentage.doubleValue()).replace('.', ',');
    }
}