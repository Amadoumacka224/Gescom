import i18n from '../i18n';
import { paymentMethodLabelKey } from '../constants/paymentMethods';
import { formatAmount, formatCurrency, formatDate } from './format';

// La facture est un document destiné au client : elle est éditée dans la langue de
// l'interface, celle dans laquelle l'utilisateur travaille au moment de l'émission.
const t = (key, values) => i18n.t(key, values);
import { DEFAULT_TAX_RATE } from '../constants/billing';

/*
 * jspdf et jspdf-autotable pèsent à eux deux plus de 400 ko, pour deux boutons — éditer une
 * facture et éditer un avoir. Importés en tête de module, ils partaient dans le paquet
 * d'entrée et se téléchargeaient à l'ouverture de la page de connexion, où personne n'édite
 * quoi que ce soit. Ils sont désormais chargés au premier appel, puis conservés : rouvrir une
 * facture ne les retélécharge pas.
 *
 * Les deux bibliothèques sont liées à des variables de module plutôt que passées en argument :
 * `autoTable` est utilisé par les fonctions de dessin plus bas, qui restent ainsi inchangées.
 * Elles ne sont appelées que depuis les deux générateurs exportés, lesquels attendent le
 * chargement avant toute chose — la liaison est donc toujours faite au moment de leur usage.
 */
let jsPDF;
let autoTable;
let pdfLibsPromise;

const loadPdfLibs = () => {
  if (!pdfLibsPromise) {
    pdfLibsPromise = Promise.all([import('jspdf'), import('jspdf-autotable')])
      .then(([pdfModule, tableModule]) => {
        jsPDF = pdfModule.default;
        autoTable = tableModule.default;
      })
      .catch((error) => {
        // Sans cette remise à zéro, un échec réseau ponctuel condamnerait l'édition de PDF
        // pour toute la durée de la session : la promesse rejetée serait réutilisée telle
        // quelle à chaque tentative suivante.
        pdfLibsPromise = undefined;
        throw error;
      });
  }
  return pdfLibsPromise;
};

// Configuration des couleurs — reprise de la charte (cf. `tailwind.config.js`)
const primaryColor = [31, 119, 180]; // #1F77B4 — bleu acier (primary-600)
const secondaryColor = [100, 118, 151]; // #647697 — gris bleuté (gray-500)
const darkColor = [26, 39, 64]; // #1A2740 — bleu nuit (gray-900)

/**
 * Emblème de l'en-tête : la flèche entrante de l'application, reprise telle quelle de l'icône
 * `log-in` de lucide (la même que sur l'écran de connexion), redessinée en vectoriel.
 *
 * Le tracé garde le repère de 24 unités de l'icône d'origine et se met à l'échelle demandée :
 * les coordonnées ci-dessous se lisent donc en regard du SVG de lucide, segment par segment.
 * `doc.lines` attend des déplacements relatifs, comme les commandes minuscules d'un chemin SVG,
 * et les quarts de cercle des angles sont approchés par des Bézier cubiques (k = 0,5523 × r),
 * jsPDF ne traçant pas d'arc.
 */
const drawLoginGlyph = (doc, x, y, size, color) => {
  const s = size / 24;
  const at = (ux, uy) => [x + ux * s, y + uy * s];

  doc.setDrawColor(...color);
  doc.setLineWidth(2 * s);
  doc.setLineCap('round');
  doc.setLineJoin('round');

  // « m10 17 5-5-5-5 » — la pointe de la flèche.
  doc.lines([[5, -5], [-5, -5]], ...at(10, 17), [s, s], 'S', false);
  // « M15 12H3 » — sa hampe.
  doc.lines([[-12, 0]], ...at(15, 12), [s, s], 'S', false);
  // « M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4 » — le montant de la porte, angles arrondis.
  doc.lines([
    [4, 0],
    [1.1046, 0, 2, 0.8954, 2, 2],
    [0, 14],
    [0, 1.1046, -0.8954, 2, -2, 2],
    [-4, 0],
  ], ...at(15, 3), [s, s], 'S', false);

  // Les réglages de trait sont globaux au document : sans remise à zéro, le filet du
  // récapitulatif et les cadres tracés plus bas hériteraient de cette épaisseur et de ces bouts
  // arrondis.
  doc.setLineWidth(0.2);
  doc.setLineCap('butt');
  doc.setLineJoin('miter');
};

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

/*
 * Montants et dates sont formatés comme partout ailleurs dans l'application (`utils/format`) :
 * la facture sortait « 1234.50 € » et « 2026-08-18 » là où l'écran affiche « 1 234,50 € » et
 * « 18/08/2026 » pour la même donnée — le client recevait un document qui ne ressemblait pas à
 * celui que le vendeur avait sous les yeux.
 *
 * `pdfSafe` ramène toute espace à l'espace ordinaire : jsPDF n'embarque que les polices
 * standard, encodées en WinAnsi, où l'espace fine insécable qu'`Intl` place avant le symbole
 * monétaire en fr-BE (U+202F) n'existe pas et sortirait en caractère parasite.
 */
const pdfSafe = (text) => String(text).replace(/\s/g, ' ');
const money = (amount) => pdfSafe(formatCurrency(amount));
const day = (value) => pdfSafe(formatDate(value));

/**
 * Hauteur réservée en bas de page (bandeau de couleur + mention légale). Rien ne doit être
 * écrit dessous : jsPDF ne déborde pas sur une page suivante, il perd silencieusement ce qui
 * sort de la page.
 */
const FOOTER_ZONE = 24;

/** Ordonnée du premier contenu d'une page de suite, juste sous le bandeau d'en-tête. */
const CONTENT_TOP = 56;

/**
 * En-tête commun aux documents client : bandeau de couleur, raison sociale et coordonnées
 * légales. Facture et avoir sortent de la même entreprise, elles s'annoncent de la
 * même façon — c'est ce qui les fait reconnaître comme deux pièces d'un même dossier.
 */
const drawHeader = (doc, company) => {
  const headerHeight = 46;
  doc.setFillColor(...primaryColor);
  doc.rect(0, 0, 210, headerHeight, 'F');

  /*
   * Emblème : pastille blanche portant la flèche entrante de l'application, celle de l'écran de
   * connexion et de la barre latérale — le document se rattache ainsi visuellement à l'outil
   * qui l'a produit.
   *
   * Tracé en vectoriel plutôt qu'en image : rien à charger, rien à encoder en base64, et le
   * rendu reste net à l'impression quelle que soit la définition. `assets/logo.svg` n'est pas
   * utilisable ici — c'est une plaque portant le mot « GESCOM » en dur, pas une marque.
   */
  const badgeX = 15;
  const badgeY = 11;
  const badgeSize = 14;
  doc.setFillColor(255, 255, 255);
  doc.roundedRect(badgeX, badgeY, badgeSize, badgeSize, 3, 3, 'F');

  const glyphSize = 8.5;
  const glyphOffset = (badgeSize - glyphSize) / 2;
  drawLoginGlyph(doc, badgeX + glyphOffset, badgeY + glyphOffset, glyphSize, primaryColor);

  // Raison sociale, à droite de l'emblème. Le corps se réduit jusqu'à ce que le nom tienne dans
  // la laisse laissée libre par les coordonnées de droite : une raison sociale longue passait
  // sinon par-dessus l'adresse.
  const nameX = badgeX + badgeSize + 5;
  const nameWidth = 145 - nameX;
  doc.setTextColor(255, 255, 255);
  let nameSize = 22;
  doc.setFontSize(nameSize);
  while (nameSize > 12 && doc.getTextWidth(company.name) > nameWidth) {
    nameSize -= 1;
    doc.setFontSize(nameSize);
  }
  doc.text(company.name, nameX, 18);

  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.text(t('app.tagline'), nameX, 25);

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

/** Mention légale belge (raison sociale + n° TVA/BCE), pagination et bandeau de bas de page. */
const drawFooter = (doc, company, settings, page, pageCount) => {
  const pageHeight = doc.internal.pageSize.height;
  doc.setFontSize(8);
  doc.setTextColor(...secondaryColor);
  doc.setFont('helvetica', 'italic');

  const legalParts = [company.name];
  if (company.vat) legalParts.push(t('pdf.vatLine', { vat: company.vat }));
  const footerText = `${legalParts.join(' — ')} | ${settings.footerText || t('settings.footerText')}`;
  // Le texte de pied vient des Réglages, sa longueur est libre : borné à 145 mm pour ne pas
  // courir sous la pagination, et réduit à sa première ligne — le bas de page n'en tient pas
  // deux sans mordre sur le bandeau.
  doc.text(doc.splitTextToSize(footerText, 145)[0], 105, pageHeight - 15, { align: 'center' });

  doc.setFont('helvetica', 'normal');
  doc.text(t('pdf.pageLine', { page, pageCount }), 195, pageHeight - 15, { align: 'right' });

  doc.setFillColor(...primaryColor);
  doc.rect(0, pageHeight - 10, 210, 10, 'F');
};

/**
 * Pied de page apposé sur *toutes* les pages, une fois le document terminé : la pagination
 * annonce le nombre total de pages, qu'on ne connaît qu'à la fin.
 */
const stampFooters = (doc, company, settings) => {
  const pageCount = doc.internal.getNumberOfPages();
  for (let page = 1; page <= pageCount; page += 1) {
    doc.setPage(page);
    drawFooter(doc, company, settings, page, pageCount);
  }
};

/**
 * Réserve `needed` millimètres sous `y`, en ouvrant une page si le pied de page est trop
 * proche, et retourne l'ordonnée où écrire. Sans cette garde, un récapitulatif ou des notes
 * arrivant en bas de page s'écrivaient par-dessus le bandeau, voire hors de la feuille.
 */
const ensureSpace = (doc, company, y, needed) => {
  if (y + needed <= doc.internal.pageSize.height - FOOTER_ZONE) return y;
  doc.addPage();
  drawHeader(doc, company);
  return CONTENT_TOP;
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

export const generateInvoicePDF = async (invoice, settings = {}) => {
  await loadPdfLibs();
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
    // La pièce d'origine : une facture dit de quelle commande elle sort, comme l'avoir dit de
    // quelle facture il sort. C'est ce qui permet au client de rapprocher les deux documents.
    { label: t('pdf.orderLabel'), value: invoice.order?.orderNumber || '—' },
    { label: t('pdf.dateLabel'), value: day(invoice.invoiceDate) },
    { label: t('pdf.dueDateLabel'), value: day(invoice.dueDate) },
  ];

  let yPos = 55;
  invoiceDetails.forEach(detail => {
    doc.setFont('helvetica', 'bold');
    doc.text(detail.label, 133, yPos);
    doc.setFont('helvetica', 'normal');
    doc.text(detail.value, 165, yPos);
    yPos += 6;
  });

  /*
   * Adresse de facturation complète : sans code postal ni ville, ce n'est pas une adresse, et
   * la raison sociale d'un client professionnel doit figurer sur la pièce. Le cadre s'ajuste au
   * nombre de lignes plutôt que de rester à une hauteur fixe où les dernières débordaient du
   * fond gris.
   */
  const client = invoice.order?.client;
  // Le repli de police doit être posé avant le calcul : `splitTextToSize` mesure avec la
  // police courante, et une adresse longue doit se replier dans le cadre, pas en sortir.
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  const clientLines = (client
    ? [
        client.company,
        client.name || [client.firstName, client.lastName].filter(Boolean).join(' '),
        client.address,
        [client.postalCode, client.city].filter(Boolean).join(' '),
        client.country,
        client.email,
        client.phone,
      ].filter(Boolean)
    // Vente de passage : la commande n'a pas de client, la facture le dit plutôt que de laisser
    // un cadre vide qu'on prendrait pour un oubli.
    : [t('pdf.walkInClient')]
  ).flatMap(line => doc.splitTextToSize(String(line), 82));

  const clientBoxTop = 75;
  const clientBoxHeight = 15 + clientLines.length * 5;
  doc.setFillColor(243, 244, 246); // bg-gray-100
  doc.rect(15, clientBoxTop, 90, clientBoxHeight, 'F');

  doc.setFontSize(11);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...darkColor);
  doc.text(t('pdf.billedTo'), 20, clientBoxTop + 7);

  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);

  yPos = clientBoxTop + 15;
  clientLines.forEach(line => {
    doc.text(line, 20, yPos);
    yPos += 5;
  });

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

  // Tableau des articles, sous le plus bas du cadre client et du pavé de statut.
  const tableStartY = Math.max(120, clientBoxTop + clientBoxHeight + 10);

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
        money(item.unitPrice)
      ];
      if (hasLineDiscount) {
        row.push(Number(item.discount) > 0 ? `-${pdfSafe(formatAmount(item.discount))} %` : '—');
      }
      row.push(money(lineNetTotal(item)));
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
      /*
       * Le tableau est calé sur la laisse du document (15 → 195 mm), celle de l'en-tête, du
       * titre et du récapitulatif : à la marge par défaut d'autoTable (40 pt ≈ 14,1 mm) il
       * commençait 1 mm trop à gauche et débordait de 4 mm à droite, autoTable signalant en
       * console la largeur qu'il n'arrivait pas à faire tenir.
       *
       * `top` place les pages de suite sous le bandeau d'en-tête, `bottom` réserve le pied de
       * page : une ligne d'articles ne doit jamais s'écrire par-dessus.
       */
      margin: { top: CONTENT_TOP, left: 15, right: 15, bottom: FOOTER_ZONE },
      // L'en-tête est redessiné à chaque page : une facture qui tient sur deux feuilles ne doit
      // pas en sortir une seconde, anonyme, sans raison sociale ni n° de TVA.
      didDrawPage: () => drawHeader(doc, company),
      // Largeurs redistribuées à somme constante (180 mm) quand la colonne « Remise » s'ajoute :
      // le tableau occupe la même laisse, aucun débordement de page à craindre.
      columnStyles: hasLineDiscount
        ? {
            0: { cellWidth: 60 },
            1: { cellWidth: 30 },
            2: { cellWidth: 15, halign: 'center' },
            3: { cellWidth: 25, halign: 'right' },
            4: { cellWidth: 22, halign: 'right' },
            5: { cellWidth: 28, halign: 'right' }
          }
        : {
            0: { cellWidth: 72 },
            1: { cellWidth: 33 },
            2: { cellWidth: 17, halign: 'center' },
            3: { cellWidth: 28, halign: 'right' },
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

  /*
   * Les montants sont repris tels quels de la facture enregistrée — le document ne recalcule
   * rien, même quand la TVA est nulle.
   *
   * Le repli qui, à taux et montant de TVA nuls, refacturait au taux des Réglages produisait
   * une facture réclamant 21 % de plus que la ligne inscrite en base : une vente exonérée ou
   * saisie hors TVA sortait avec un total que ni l'écran Factures ni le registre ne portaient.
   * Un document commercial n'invente pas son total : une facture sans TVA se corrige en base,
   * pas à l'impression. C'est la règle déjà appliquée à l'avoir.
   */
  const subtotal = Number(invoice.subtotal) || 0;
  const discount = Number(invoice.discount) || 0;
  const taxRate = Number(invoice.taxRate) || 0;
  const taxAmount = Number(invoice.taxAmount) || 0;
  const totalAmount = Number(invoice.totalAmount) || 0;
  const paidAmount = Number(invoice.paidAmount) || 0;
  const remainingAmount = Number(invoice.remainingAmount ?? (totalAmount - paidAmount)) || 0;

  const financialSummary = [
    { label: t('pdf.subtotalLabel'), value: money(subtotal), bold: false },
  ];

  if (discount > 0) {
    financialSummary.push({
      label: t('pdf.discountLabel'),
      value: `-${money(discount)}`,
      bold: false,
      color: [239, 68, 68]
    });
  }

  // Toujours afficher la TVA
  financialSummary.push(
    { label: `TVA (${taxRate}%):`, value: `+${money(taxAmount)}`, bold: false, color: [59, 130, 246] },
    { label: t('pdf.totalLabel'), value: money(totalAmount), bold: true, size: 12 }
  );

  if (paidAmount > 0) {
    financialSummary.push(
      { label: t('pdf.paidLabel'), value: money(paidAmount), bold: false, color: [34, 197, 94] },
      { label: t('pdf.remainingLabel'), value: money(remainingAmount), bold: true, color: [251, 146, 60] }
    );
  }

  // Le récapitulatif ne se coupe pas : s'il ne tient pas entier au bas de la page, il passe
  // à la suivante d'un bloc.
  const summaryHeight = financialSummary.reduce((height, item) => height + (item.bold ? 10 : 6), 0);
  let summaryY = ensureSpace(doc, company, finalY, summaryHeight);

  // Coordonnées bancaires (mention légale belge pour le paiement) : en colonne de gauche, en
  // regard des totaux, là où la moitié de la page est libre. Placées après les notes, elles
  // finissaient au ras du pied de page et basculaient seules sur une deuxième feuille.
  drawBankDetails(doc, company, summaryY);

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
    summaryY = ensureSpace(doc, company, summaryY + 5, invoice.paymentDate ? 10 : 5);
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
      doc.text(t('pdf.paymentDateLine', { date: day(invoice.paymentDate) }), summaryX, summaryY);
    }
  }

  // Notes
  if (invoice.notes) {
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    const notesLines = doc.splitTextToSize(invoice.notes, 180);
    summaryY = ensureSpace(doc, company, summaryY + 10, 6 + notesLines.length * 5);

    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...darkColor);
    doc.text(t('pdf.notesLabel'), 15, summaryY);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    doc.text(notesLines, 15, summaryY + 6);
    // Les notes descendent d'autant de lignes qu'elles en comptent : sans cela, les
    // coordonnées bancaires venaient s'écrire par-dessus dès qu'une note tenait sur deux lignes.
    summaryY += 6 + notesLines.length * 5;
  }

  stampFooters(doc, company, settings);

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
export const generateCreditNotePDF = async (stockReturn, settings = {}) => {
  await loadPdfLibs();
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
    { label: t('pdf.dateLabel'), value: day(stockReturn.createdAt) },
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
    : [t('pdf.walkInClient')];

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
        amountOf(item) > 0 ? money(amountOf(item)) : '—',
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
      // `top` place les pages de suite sous le bandeau d'en-tête, `bottom` réserve le pied.
      margin: { top: CONTENT_TOP, left: 15, right: 15, bottom: FOOTER_ZONE },
      didDrawPage: () => drawHeader(doc, company),
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

  const subtotal = Number(stockReturn.refundAmount) || 0;
  const taxRate = Number(stockReturn.taxRate ?? settings.taxRate ?? DEFAULT_TAX_RATE) || 0;
  const taxAmount = subtotal * taxRate / 100;

  const summary = [
    { label: t('pdf.subtotalLabel'), value: money(subtotal) },
    { label: `TVA (${taxRate}%):`, value: `+${money(taxAmount)}`, color: [59, 130, 246] },
    { label: t('pdf.creditNote.refundTotalLabel'), value: money(subtotal + taxAmount), bold: true, size: 12 },
  ];

  const summaryHeight = summary.reduce((height, item) => height + (item.bold ? 10 : 6), 0);
  const tableEnd = doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10;
  let summaryY = ensureSpace(doc, company, tableEnd, summaryHeight);
  // La quantité rendue se lit en regard du montant : elle suit donc le récapitulatif de page,
  // sans quoi elle resterait seule au bas de la feuille précédente.
  const quantityY = summaryY;

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
    quantityY
  );

  // Coordonnées bancaires : c'est par là que le remboursement partira. Comme sur la facture,
  // elles occupent la colonne de gauche laissée libre par le récapitulatif.
  if (subtotal > 0) {
    drawBankDetails(doc, company, quantityY + 10);
  }

  // Notes du retour
  if (stockReturn.notes) {
    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    const notesLines = doc.splitTextToSize(stockReturn.notes, 180);
    summaryY = ensureSpace(doc, company, summaryY + 10, 6 + notesLines.length * 5);

    doc.setFontSize(10);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...darkColor);
    doc.text(t('pdf.notesLabel'), 15, summaryY);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...secondaryColor);
    doc.text(notesLines, 15, summaryY + 6);
    summaryY += 6 + notesLines.length * 5;
  }

  stampFooters(doc, company, settings);

  const fileName = `${t('pdf.creditNote.fileNamePrefix')}_${stockReturn.returnNumber}_${new Date().getTime()}.pdf`;
  doc.save(fileName);
};
