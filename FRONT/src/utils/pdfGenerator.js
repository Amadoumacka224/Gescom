import jsPDF from 'jspdf';
import 'jspdf-autotable';

export const generateInvoicePDF = (invoice) => {
  const doc = new jsPDF();

  // Configuration des couleurs
  const primaryColor = [59, 130, 246]; // #3B82F6
  const secondaryColor = [107, 114, 128]; // #6B7280
  const darkColor = [17, 24, 39]; // #111827

  // En-tête avec logo et informations de l'entreprise
  doc.setFillColor(...primaryColor);
  doc.rect(0, 0, 210, 40, 'F');

  // Logo GESCOM
  doc.setFontSize(24);
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.text('GESCOM', 15, 20);

  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.text('Gestion Commerciale', 15, 27);

  // Informations de l'entreprise (à droite)
  doc.setFontSize(9);
  doc.setTextColor(255, 255, 255);
  const companyInfo = [
    'GESCOM SARL',
    '123 Rue du Commerce',
    '75001 Paris, France',
    'Tel: +33 1 23 45 67 89',
    'Email: contact@gescom.fr'
  ];
  let yPos = 12;
  companyInfo.forEach(line => {
    doc.text(line, 195, yPos, { align: 'right' });
    yPos += 5;
  });

  // Titre FACTURE
  doc.setFontSize(20);
  doc.setTextColor(...darkColor);
  doc.setFont('helvetica', 'bold');
  doc.text('FACTURE', 15, 55);

  // Numéro de facture et dates
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  const invoiceDetails = [
    { label: 'N° Facture:', value: invoice.invoiceNumber },
    { label: 'Date:', value: invoice.invoiceDate },
    { label: 'Échéance:', value: invoice.dueDate },
  ];

  yPos = 55;
  invoiceDetails.forEach(detail => {
    doc.setFont('helvetica', 'bold');
    doc.text(detail.label, 140, yPos);
    doc.setFont('helvetica', 'normal');
    doc.text(detail.value, 165, yPos);
    yPos += 6;
  });

  // Informations du client
  doc.setFillColor(243, 244, 246); // bg-gray-100
  doc.rect(15, 75, 90, 35, 'F');

  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...darkColor);
  doc.text('FACTURÉ À:', 20, 82);

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  if (invoice.order?.client) {
    const client = invoice.order.client;
    const clientInfo = [
      client.name || `${client.firstName} ${client.lastName}`,
      client.email || '',
      client.phone || '',
      client.address || ''
    ].filter(info => info);

    yPos = 90;
    clientInfo.forEach(line => {
      doc.text(line, 20, yPos);
      yPos += 5;
    });
  }

  // Statut de paiement
  const statusColors = {
    'PAID': [34, 197, 94],
    'UNPAID': [239, 68, 68],
    'PARTIALLY_PAID': [251, 146, 60],
    'CANCELED': [156, 163, 175]
  };
  const statusTexts = {
    'PAID': 'PAYÉE',
    'UNPAID': 'NON PAYÉE',
    'PARTIALLY_PAID': 'PARTIELLEMENT PAYÉE',
    'CANCELED': 'ANNULÉE'
  };

  const statusColor = statusColors[invoice.status] || statusColors.UNPAID;
  const statusText = statusTexts[invoice.status] || 'NON PAYÉE';

  doc.setFillColor(...statusColor);
  doc.roundedRect(125, 85, 70, 12, 2, 2, 'F');
  doc.setFontSize(10);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(255, 255, 255);
  doc.text(statusText, 160, 92, { align: 'center' });

  // Tableau des articles
  const tableStartY = 120;

  if (invoice.order?.orderItems && invoice.order.orderItems.length > 0) {
    const tableData = invoice.order.orderItems.map(item => [
      item.product?.name || 'Produit',
      item.product?.reference || '-',
      item.quantity.toString(),
      `${item.unitPrice.toFixed(2)} €`,
      `${(item.quantity * item.unitPrice).toFixed(2)} €`
    ]);

    doc.autoTable({
      startY: tableStartY,
      head: [['Produit', 'Référence', 'Qté', 'Prix unitaire', 'Total']],
      body: tableData,
      theme: 'striped',
      headStyles: {
        fillColor: primaryColor,
        textColor: [255, 255, 255],
        fontStyle: 'bold',
        fontSize: 10
      },
      styles: {
        fontSize: 9,
        cellPadding: 5
      },
      columnStyles: {
        0: { cellWidth: 70 },
        1: { cellWidth: 35 },
        2: { cellWidth: 20, halign: 'center' },
        3: { cellWidth: 30, halign: 'right' },
        4: { cellWidth: 30, halign: 'right' }
      }
    });
  }

  // Récapitulatif financier
  const finalY = doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10;

  // Rectangle pour le récapitulatif
  doc.setDrawColor(...secondaryColor);
  doc.setLineWidth(0.5);

  const summaryX = 120;
  let summaryY = finalY;

  // Calculs des montants avec valeurs par défaut
  const subtotal = invoice.subtotal || 0;
  const discount = invoice.discount || 0;

  // Récupérer le taux de TVA (par défaut 20% si non défini ou 0)
  let taxRate = invoice.taxRate || 0;
  let taxAmount = invoice.taxAmount || 0;
  let totalAmount = invoice.totalAmount || 0;

  // Si la TVA n'est pas calculée (anciennes factures), la recalculer
  if (taxAmount === 0 && taxRate === 0 && subtotal > 0) {
    // Appliquer un taux de TVA par défaut de 20%
    taxRate = 20;
    const subtotalAfterDiscount = subtotal - discount;
    taxAmount = subtotalAfterDiscount * taxRate / 100;
    totalAmount = subtotalAfterDiscount + taxAmount;
  }

  const paidAmount = invoice.paidAmount || 0;
  const remainingAmount = invoice.remainingAmount || (totalAmount - paidAmount);

  const financialSummary = [
    { label: 'Sous-total HT:', value: `${subtotal.toFixed(2)} €`, bold: false },
  ];

  if (discount > 0) {
    financialSummary.push({
      label: 'Remise:',
      value: `-${discount.toFixed(2)} €`,
      bold: false,
      color: [239, 68, 68]
    });
  }

  // Toujours afficher la TVA
  financialSummary.push(
    { label: `TVA (${taxRate}%):`, value: `+${taxAmount.toFixed(2)} €`, bold: false, color: [59, 130, 246] },
    { label: 'TOTAL TTC:', value: `${totalAmount.toFixed(2)} €`, bold: true, size: 12 }
  );

  if (paidAmount > 0) {
    financialSummary.push(
      { label: 'Montant payé:', value: `${paidAmount.toFixed(2)} €`, bold: false, color: [34, 197, 94] },
      { label: 'Reste à payer:', value: `${remainingAmount.toFixed(2)} €`, bold: true, color: [251, 146, 60] }
    );
  }

  financialSummary.forEach(item => {
    doc.setFontSize(item.size || 10);
    doc.setFont('helvetica', item.bold ? 'bold' : 'normal');
    doc.setTextColor(...(item.color || secondaryColor));

    doc.text(item.label, summaryX, summaryY);
    doc.text(item.value, 195, summaryY, { align: 'right' });

    summaryY += item.bold ? 8 : 6;

    if (item.bold && item !== financialSummary[financialSummary.length - 1]) {
      doc.setDrawColor(...secondaryColor);
      doc.line(summaryX, summaryY - 2, 195, summaryY - 2);
      summaryY += 2;
    }
  });

  // Méthode de paiement
  if (invoice.paymentMethod) {
    const paymentMethods = {
      'CASH': 'Espèces',
      'CREDIT_CARD': 'Carte de crédit',
      'DEBIT_CARD': 'Carte de débit',
      'BANK_TRANSFER': 'Virement bancaire',
      'CHECK': 'Chèque',
      'MOBILE_PAYMENT': 'Paiement mobile'
    };

    summaryY += 5;
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    doc.text(`Méthode: ${paymentMethods[invoice.paymentMethod] || invoice.paymentMethod}`, summaryX, summaryY);

    if (invoice.paymentDate) {
      summaryY += 5;
      doc.text(`Date de paiement: ${invoice.paymentDate}`, summaryX, summaryY);
    }
  }

  // Notes
  if (invoice.notes) {
    summaryY += 10;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...darkColor);
    doc.text('Notes:', 15, summaryY);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    const notesLines = doc.splitTextToSize(invoice.notes, 180);
    doc.text(notesLines, 15, summaryY + 6);
  }

  // Pied de page
  const pageHeight = doc.internal.pageSize.height;
  doc.setFontSize(8);
  doc.setTextColor(...secondaryColor);
  doc.setFont('helvetica', 'italic');

  const footerText = 'Merci pour votre confiance | GESCOM - Votre partenaire de gestion commerciale';
  doc.text(footerText, 105, pageHeight - 15, { align: 'center' });

  doc.setFillColor(...primaryColor);
  doc.rect(0, pageHeight - 10, 210, 10, 'F');

  // Générer le PDF
  const fileName = `Facture_${invoice.invoiceNumber}_${new Date().getTime()}.pdf`;
  doc.save(fileName);
};
