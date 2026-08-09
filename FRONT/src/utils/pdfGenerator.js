import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

import i18n from '../i18n';
import { paymentMethodLabelKey } from '../constants/paymentMethods';
import { formatDate } from './format';

// La facture est un document destiné au client : elle est éditée dans la langue de
// l'interface, celle dans laquelle l'utilisateur travaille au moment de l'émission.
const t = (key, values) => i18n.t(key, values);
import { DEFAULT_TAX_RATE } from '../constants/billing';

// Configuration des couleurs — reprise de la charte (cf. `tailwind.config.js`)
const primaryColor = [31, 119, 180]; // #1F77B4 — bleu acier (primary-600)
const secondaryColor = [100, 118, 151]; // #647697 — gris bleuté (gray-500)
const darkColor = [26, 39, 64]; // #1A2740 — bleu nuit (gray-900)

/** Informations de l'entreprise issues des Réglages (avec valeurs par défaut belges). */
const companyFrom = (settings) => ({
  name: settings.companyName || 'GESCOM',
  address: settings.companyAddress || '',
  postal: settings.companyPostalCode || '',
  city: settings.companyCity || '',
  country: settings.companyCountry || t('pdf.defaultCountry'),
  phone: settings.companyPhone || '',
  email: settings.companyEmail || '',
  vat: settings.companyTaxId || '',   // N° TVA = n° d'entreprise (BCE) en Belgique
  iban: settings.companyIban || '',
  bic: settings.companyBic || '',
});

/**
 * En-tête commun aux documents client : bandeau de couleur, raison sociale et coordonnées
 * légales. Facture et avoir sortent de la même entreprise, elles s'annoncent de la
 * même façon — c'est ce qui les fait reconnaître comme deux pièces d'un même dossier.
 */
const drawHeader = (doc, company) => {
  const headerHeight = 46;
  doc.setFillColor(...primaryColor);
  doc.rect(0, 0, 210, headerHeight, 'F');

  // Logo / raison sociale
  doc.setFontSize(22);
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.text(company.name, 15, 18);

  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.text(t('app.tagline'), 15, 25);

  // Coordonnées et références légales belges (à droite)
  doc.setFontSize(8);
  doc.setTextColor(255, 255, 255);
  const cityLine = [company.postal, company.city].filter(Boolean).join(' ');
  const companyInfo = [
    company.address,
    [cityLine, company.country].filter(Boolean).join(', '),
    company.phone ? `Tél : ${company.phone}` : '',
    company.email ? `Email : ${company.email}` : '',
    company.vat ? `N° TVA / BCE : ${company.vat}` : '',
  ].filter(Boolean);
  let yPos = 9;
  companyInfo.forEach(line => {
    doc.text(line, 195, yPos, { align: 'right' });
    yPos += 5;
  });
};

/** Mention légale belge (raison sociale + n° TVA/BCE) et bandeau de bas de page. */
const drawFooter = (doc, company, settings) => {
  const pageHeight = doc.internal.pageSize.height;
  doc.setFontSize(8);
  doc.setTextColor(...secondaryColor);
  doc.setFont('helvetica', 'italic');

  const legalParts = [company.name];
  if (company.vat) legalParts.push(t('pdf.vatLine', { vat: company.vat }));
  const footerText = `${legalParts.join(' — ')} | ${settings.footerText || t('settings.footerText')}`;
  doc.text(footerText, 105, pageHeight - 15, { align: 'center' });

  doc.setFillColor(...primaryColor);
  doc.rect(0, pageHeight - 10, 210, 10, 'F');
};

/** Bloc « coordonnées bancaires » : mention de paiement, sous le récapitulatif. */
const drawBankDetails = (doc, company, y) => {
  const bankParts = [];
  if (company.iban) bankParts.push(`IBAN : ${company.iban}`);
  if (company.bic) bankParts.push(`BIC : ${company.bic}`);
  if (bankParts.length === 0) return;

  doc.setFontSize(9);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...darkColor);
  doc.text(t('settings.bankDetailsTitle'), 15, y);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);
  doc.text(bankParts.join('   •   '), 15, y + 6);
};

export const generateInvoicePDF = (invoice, settings = {}) => {
  const doc = new jsPDF();
  const company = companyFrom(settings);

  drawHeader(doc, company);

  // Titre FACTURE
  doc.setFontSize(20);
  doc.setTextColor(...darkColor);
  doc.setFont('helvetica', 'bold');
  doc.text(t('pdf.invoiceHeading'), 15, 55);

  // Numéro de facture et dates
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  const invoiceDetails = [
    { label: t('pdf.invoiceNumberLabel'), value: invoice.invoiceNumber || '-' },
    { label: t('pdf.dateLabel'), value: String(invoice.invoiceDate || '-') },
    { label: t('pdf.dueDateLabel'), value: String(invoice.dueDate || '-') },
  ];

  let yPos = 55;
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
  doc.text(t('pdf.billedTo'), 20, 82);

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
    'PAID': t('pdf.status.PAID'),
    'UNPAID': t('pdf.status.UNPAID'),
    'PARTIALLY_PAID': t('pdf.status.PARTIALLY_PAID'),
    'CANCELED': t('pdf.status.CANCELED')
  };

  const statusColor = statusColors[invoice.status] || statusColors.UNPAID;
  const statusText = statusTexts[invoice.status] || statusTexts.UNPAID;

  doc.setFillColor(...statusColor);
  doc.roundedRect(125, 85, 70, 12, 2, 2, 'F');
  doc.setFontSize(10);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(255, 255, 255);
  doc.text(statusText, 160, 92, { align: 'center' });

  // Tableau des articles
  const tableStartY = 120;

  const items = invoice.order?.items || [];

  // Total d'une ligne : celui calculé et stocké par le backend (`OrderItem.totalPrice`), déjà
  // net de la remise de ligne. Le recalculer en quantité × prix unitaire afficherait un montant
  // brut qui ne s'additionne pas au sous-total du récapitulatif — la facture ne tomberait plus
  // juste aux yeux du client. Le repli ne sert qu'aux réponses sans totalPrice.
  const lineNetTotal = (item) => Number(item.totalPrice ?? (item.quantity * item.unitPrice)) || 0;

  // La colonne « Remise » n'apparaît que si au moins une ligne en porte une : une vente sans
  // remise garde exactement la mise en page habituelle.
  const hasLineDiscount = items.some((item) => Number(item.discount) > 0);

  if (items.length > 0) {
    const tableData = items.map(item => {
      const row = [
        item.product?.name || t('common.product'),
        item.product?.reference || '-',
        item.quantity.toString(),
        `${item.unitPrice.toFixed(2)} €`
      ];
      if (hasLineDiscount) {
        row.push(Number(item.discount) > 0 ? `-${Number(item.discount).toFixed(2)} %` : '—');
      }
      row.push(`${lineNetTotal(item).toFixed(2)} €`);
      return row;
    });

    autoTable(doc, {
      startY: tableStartY,
      head: [hasLineDiscount
        ? [t('common.product'), t('pdf.reference'), t('orders.recap.qtyShort'), t('orders.unitPrice'), t('common.discount'), t('common.total')]
        : [t('common.product'), t('pdf.reference'), t('orders.recap.qtyShort'), t('orders.unitPrice'), t('common.total')]],
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
      // Largeurs redistribuées à somme constante quand la colonne « Remise » s'ajoute : le
      // tableau occupe la même laisse, aucun débordement de page à craindre.
      columnStyles: hasLineDiscount
        ? {
            0: { cellWidth: 62 },
            1: { cellWidth: 30 },
            2: { cellWidth: 15, halign: 'center' },
            3: { cellWidth: 28, halign: 'right' },
            4: { cellWidth: 22, halign: 'right' },
            5: { cellWidth: 28, halign: 'right' }
          }
        : {
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

  // Récupérer le taux de TVA
  let taxRate = invoice.taxRate || 0;
  let taxAmount = invoice.taxAmount || 0;
  let totalAmount = invoice.totalAmount || 0;

  // Si la TVA n'est pas calculée (anciennes factures), la recalculer
  if (taxAmount === 0 && taxRate === 0 && subtotal > 0) {
    // Taux de TVA par défaut : celui des Réglages, sinon le repli commun (taux standard belge)
    taxRate = Number(settings.taxRate) || DEFAULT_TAX_RATE;
    const subtotalAfterDiscount = subtotal - discount;
    taxAmount = subtotalAfterDiscount * taxRate / 100;
    totalAmount = subtotalAfterDiscount + taxAmount;
  }

  const paidAmount = invoice.paidAmount || 0;
  const remainingAmount = invoice.remainingAmount || (totalAmount - paidAmount);

  const financialSummary = [
    { label: t('pdf.subtotalLabel'), value: `${subtotal.toFixed(2)} €`, bold: false },
  ];

  if (discount > 0) {
    financialSummary.push({
      label: t('pdf.discountLabel'),
      value: `-${discount.toFixed(2)} €`,
      bold: false,
      color: [239, 68, 68]
    });
  }

  // Toujours afficher la TVA
  financialSummary.push(
    { label: `TVA (${taxRate}%):`, value: `+${taxAmount.toFixed(2)} €`, bold: false, color: [59, 130, 246] },
    { label: t('pdf.totalLabel'), value: `${totalAmount.toFixed(2)} €`, bold: true, size: 12 }
  );

  if (paidAmount > 0) {
    financialSummary.push(
      { label: t('pdf.paidLabel'), value: `${paidAmount.toFixed(2)} €`, bold: false, color: [34, 197, 94] },
      { label: t('pdf.remainingLabel'), value: `${remainingAmount.toFixed(2)} €`, bold: true, color: [251, 146, 60] }
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
    summaryY += 5;
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    doc.text(
      t('pdf.methodLine', { method: t(paymentMethodLabelKey(invoice.paymentMethod)) }),
      summaryX,
      summaryY
    );

    if (invoice.paymentDate) {
      summaryY += 5;
      doc.text(t('pdf.paymentDateLine', { date: invoice.paymentDate }), summaryX, summaryY);
    }
  }

  // Notes
  if (invoice.notes) {
    summaryY += 10;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...darkColor);
    doc.text(t('pdf.notesLabel'), 15, summaryY);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    const notesLines = doc.splitTextToSize(invoice.notes, 180);
    doc.text(notesLines, 15, summaryY + 6);
  }

  // Coordonnées bancaires (mention légale belge pour le paiement)
  drawBankDetails(doc, company, summaryY + 12);

  drawFooter(doc, company, settings);

  // Générer le PDF
  const fileName = `${t('pdf.fileNamePrefix')}_${invoice.invoiceNumber}_${new Date().getTime()}.pdf`;
  doc.save(fileName);
};

/**
 * Avoir d'un retour client.
 *
 * C'est la contrepartie documentaire de la facture : la vente a produit une pièce, le retour
 * en produit une autre, qui la référence. Le document reprend les articles rendus avec leur
 * motif et leur traitement — l'avoir doit dire *pourquoi* on rembourse — et ne
 * chiffre que les lignes effectivement remboursées : une remise en stock ou un échange rend
 * de la marchandise sans rendre d'argent, et figure donc au tableau sans montant.
 *
 * Les montants viennent tels quels du retour enregistré (`refundAmount`, calculé au prix
 * réellement payé) : le document ne recalcule rien, sans quoi il pourrait annoncer un montant
 * différent de celui inscrit au registre. La TVA est celle de la facture d'origine, à défaut
 * celle des Réglages — les lignes de vente sont stockées hors taxe.
 */
export const generateCreditNotePDF = (stockReturn, settings = {}) => {
  const doc = new jsPDF();
  const company = companyFrom(settings);

  drawHeader(doc, company);

  // Titre AVOIR
  doc.setFontSize(20);
  doc.setTextColor(...darkColor);
  doc.setFont('helvetica', 'bold');
  doc.text(t('pdf.creditNote.heading'), 15, 55);

  // Références : le numéro de l'avoir, sa date, et les pièces de la vente qu'il corrige.
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  const details = [
    { label: t('pdf.creditNote.numberLabel'), value: stockReturn.returnNumber || '-' },
    { label: t('pdf.dateLabel'), value: formatDate(stockReturn.createdAt) },
    { label: t('pdf.creditNote.invoiceLabel'), value: stockReturn.invoiceNumber || '—' },
    { label: t('pdf.creditNote.orderLabel'), value: stockReturn.orderNumber || '—' },
  ];

  let yPos = 55;
  details.forEach(detail => {
    doc.setFont('helvetica', 'bold');
    doc.text(detail.label, 133, yPos);
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
  doc.text(t('pdf.creditNote.creditedTo'), 20, 82);

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  const client = stockReturn.client;
  const clientInfo = client
    ? [
        client.name || client.company || stockReturn.clientName,
        client.email,
        client.phone,
        client.address,
        [client.postalCode, client.city].filter(Boolean).join(' '),
      ].filter(Boolean)
    // Vente de passage : la commande n'a pas de client, l'avoir le dit plutôt que de laisser
    // un cadre vide qu'on prendrait pour un oubli.
    : [t('pdf.creditNote.walkInClient')];

  yPos = 90;
  clientInfo.forEach(line => {
    doc.text(line, 20, yPos);
    yPos += 5;
  });

  // Tableau des articles rendus
  const tableStartY = 120;
  const items = stockReturn.items || [];
  const amountOf = (item) => Number(item.refundAmount) || 0;

  if (items.length > 0) {
    autoTable(doc, {
      startY: tableStartY,
      head: [[
        t('common.product'),
        t('pdf.creditNote.reasonColumn'),
        t('pdf.creditNote.treatmentColumn'),
        t('orders.recap.qtyShort'),
        t('common.total'),
      ]],
      body: items.map((item) => [
        item.product?.name || t('common.product'),
        t(`stock.returns.reasons.${item.reason}`, { defaultValue: item.reason || '—' }),
        t(`stock.returns.treatments.${item.treatment}`, { defaultValue: item.treatment || '—' }),
        String(item.quantity ?? 0),
        // Sans remboursement, la ligne n'a pas de montant : un « 0,00 € » se lirait comme un
        // article rendu gratuitement, un tiret dit qu'il n'y a rien à rembourser.
        amountOf(item) > 0 ? `${amountOf(item).toFixed(2)} €` : '—',
      ]),
      theme: 'striped',
      headStyles: {
        fillColor: primaryColor,
        textColor: [255, 255, 255],
        fontStyle: 'bold',
        fontSize: 10,
      },
      styles: {
        fontSize: 9,
        cellPadding: 4,
      },
      // Le tableau est calé sur la laisse du document (15 → 195 mm), celle du titre et du
      // récapitulatif : à la marge par défaut d'autoTable, les six colonnes ne tiendraient pas
      // et leurs libellés seraient coupés.
      margin: { left: 15, right: 15 },
      columnStyles: {
        0: { cellWidth: 56 },
        1: { cellWidth: 38 },
        2: { cellWidth: 38 },
        3: { cellWidth: 16, halign: 'center' },
        4: { cellWidth: 32, halign: 'right' },
      },
    });
  }

  // Récapitulatif : sous-total HT rendu, TVA de la vente, total à rembourser.
  const summaryX = 120;
  let summaryY = doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10;

  const subtotal = Number(stockReturn.refundAmount) || 0;
  const taxRate = Number(stockReturn.taxRate ?? settings.taxRate ?? DEFAULT_TAX_RATE) || 0;
  const taxAmount = subtotal * taxRate / 100;

  const summary = [
    { label: t('pdf.subtotalLabel'), value: `${subtotal.toFixed(2)} €` },
    { label: `TVA (${taxRate}%):`, value: `+${taxAmount.toFixed(2)} €`, color: [59, 130, 246] },
    { label: t('pdf.creditNote.refundTotalLabel'), value: `${(subtotal + taxAmount).toFixed(2)} €`, bold: true, size: 12 },
  ];

  summary.forEach(item => {
    doc.setFontSize(item.size || 10);
    doc.setFont('helvetica', item.bold ? 'bold' : 'normal');
    doc.setTextColor(...(item.color || secondaryColor));

    doc.text(item.label, summaryX, summaryY);
    doc.text(item.value, 195, summaryY, { align: 'right' });

    summaryY += item.bold ? 8 : 6;

    if (item.bold && item !== summary[summary.length - 1]) {
      doc.setDrawColor(...secondaryColor);
      doc.line(summaryX, summaryY - 2, 195, summaryY - 2);
      summaryY += 2;
    }
  });

  // Quantité rendue, en regard du montant : un avoir sans montant (remise en stock, échange)
  // reste un document qui atteste d'articles repris.
  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);
  doc.text(
    t('pdf.creditNote.quantityLine', { count: stockReturn.totalQuantity ?? 0 }),
    15,
    doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10
  );

  // Notes du retour
  if (stockReturn.notes) {
    summaryY += 10;
    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...darkColor);
    doc.text(t('pdf.notesLabel'), 15, summaryY);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    doc.text(doc.splitTextToSize(stockReturn.notes, 180), 15, summaryY + 6);
  }

  // Coordonnées bancaires : c'est par là que le remboursement partira.
  if (subtotal > 0) {
    drawBankDetails(doc, company, summaryY + 12);
  }

  drawFooter(doc, company, settings);

  const fileName = `${t('pdf.creditNote.fileNamePrefix')}_${stockReturn.returnNumber}_${new Date().getTime()}.pdf`;
  doc.save(fileName);
};
