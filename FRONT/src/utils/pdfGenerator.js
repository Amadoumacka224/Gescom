import i18n from '../i18n';
import { paymentMethodLabelKey } from '../constants/paymentMethods';
import { formatAmount, formatCurrency, formatDate } from './format';

// La facture est envoyée au client : elle est générée dans la langue de l'interface,
// celle utilisée par l'utilisateur au moment où il la crée.
const t = (key, values) => i18n.t(key, values);

/*
 * jspdf et jspdf-autotable font plus de 400 ko à eux deux, pour seulement deux boutons
 * (facture et avoir). Importés en haut du fichier, ils étaient chargés dès la page de
 * connexion, où on ne génère rien. Maintenant ils sont chargés au premier appel puis gardés :
 * rouvrir une facture ne les recharge pas.
 *
 * Les deux librairies sont mises dans des variables du module plutôt que passées en
 * paramètre : `autoTable` est utilisé par les fonctions de dessin plus bas, qu'on n'a donc
 * pas eu à modifier. Elles ne sont appelées que par les deux fonctions exportées, qui
 * attendent le chargement en premier, donc les variables sont toujours prêtes.
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
        // Sans cette remise à zéro, une seule erreur réseau empêcherait de générer des PDF
        // pendant toute la session : la même promesse rejetée serait réutilisée à chaque
        // nouvel essai.
        pdfLibsPromise = undefined;
        throw error;
      });
  }
  return pdfLibsPromise;
};

// Couleurs, prises dans la charte (voir `tailwind.config.js`)
const primaryColor = [31, 119, 180]; // #1F77B4 — bleu acier (primary-600)
const secondaryColor = [100, 118, 151]; // #647697 — gris bleuté (gray-500)
const darkColor = [26, 39, 64]; // #1A2740 — bleu nuit (gray-900)

/**
 * Logo de l'en-tête : la flèche d'entrée de l'appli, recopiée de l'icône `log-in` de lucide
 * (la même que sur l'écran de connexion), redessinée en vectoriel.
 *
 * On garde la grille de 24 unités de l'icône d'origine et on met à l'échelle : on peut donc
 * comparer les coordonnées ci-dessous avec le SVG de lucide, segment par segment.
 * `doc.lines` prend des déplacements relatifs, comme les commandes en minuscule d'un chemin
 * SVG, et les coins arrondis sont faits avec des courbes de Bézier (k = 0,5523 × r) car
 * jsPDF ne sait pas tracer d'arc.
 */
const drawLoginGlyph = (doc, x, y, size, color) => {
  const s = size / 24;
  const at = (ux, uy) => [x + ux * s, y + uy * s];

  doc.setDrawColor(...color);
  doc.setLineWidth(2 * s);
  doc.setLineCap('round');
  doc.setLineJoin('round');

  // « m10 17 5-5-5-5 » : la pointe de la flèche.
  doc.lines([[5, -5], [-5, -5]], ...at(10, 17), [s, s], 'S', false);
  // « M15 12H3 » : le trait de la flèche.
  doc.lines([[-12, 0]], ...at(15, 12), [s, s], 'S', false);
  // « M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4 » : le cadre de la porte, coins arrondis.
  doc.lines([
    [4, 0],
    [1.1046, 0, 2, 0.8954, 2, 2],
    [0, 14],
    [0, 1.1046, -0.8954, 2, -2, 2],
    [-4, 0],
  ], ...at(15, 3), [s, s], 'S', false);

  // Les réglages de trait s'appliquent à tout le document : si on ne les remet pas à zéro,
  // le trait du récapitulatif et les cadres plus bas auraient la même épaisseur et les mêmes
  // bouts arrondis.
  doc.setLineWidth(0.2);
  doc.setLineCap('butt');
  doc.setLineJoin('miter');
};

/** Infos de l'entreprise venant des Paramètres (avec des valeurs par défaut belges). */
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
 * Montants et dates formatés comme dans le reste de l'appli (`utils/format`) : avant, la
 * facture affichait « 1234.50 € » et « 2026-08-18 » alors que l'écran affichait « 1 234,50 € »
 * et « 18/08/2026 » pour la même valeur. Le client recevait un document différent de ce que
 * voyait le vendeur.
 *
 * `pdfSafe` remplace tous les types d'espace par un espace normal : jsPDF n'a que les polices
 * standard (encodage WinAnsi), où l'espace fine insécable qu'`Intl` met avant le symbole €
 * en fr-BE (U+202F) n'existe pas et donnerait un caractère bizarre.
 */
const pdfSafe = (text) => String(text).replace(/\s/g, ' ');
const money = (amount) => pdfSafe(formatCurrency(amount));
const day = (value) => pdfSafe(formatDate(value));

/**
 * Hauteur réservée en bas de page (bandeau de couleur + mention légale). Il ne faut rien
 * écrire dessous : jsPDF ne passe pas tout seul à la page suivante, ce qui dépasse est
 * simplement perdu.
 */
const FOOTER_ZONE = 24;

/** Position Y du début du contenu sur les pages suivantes, juste sous le bandeau d'en-tête. */
const CONTENT_TOP = 56;

/**
 * En-tête commun aux documents client : bandeau de couleur, nom de l'entreprise et infos
 * légales. La facture et l'avoir viennent de la même entreprise, donc ils ont le même
 * en-tête, et on voit tout de suite qu'ils vont ensemble.
 */
const drawHeader = (doc, company) => {
  const headerHeight = 46;
  doc.setFillColor(...primaryColor);
  doc.rect(0, 0, 210, headerHeight, 'F');

  /*
   * Logo : rond blanc avec la flèche d'entrée de l'appli (la même que sur l'écran de
   * connexion et dans le menu), pour qu'on reconnaisse l'outil qui a généré le document.
   *
   * Dessiné en vectoriel plutôt qu'en image : rien à charger ni à encoder en base64, et ça
   * reste net à l'impression. On ne peut pas utiliser `assets/logo.svg` ici, c'est une image
   * avec le mot « GESCOM » écrit dedans, pas un logo.
   */
  const badgeX = 15;
  const badgeY = 11;
  const badgeSize = 14;
  doc.setFillColor(255, 255, 255);
  doc.roundedRect(badgeX, badgeY, badgeSize, badgeSize, 3, 3, 'F');

  const glyphSize = 8.5;
  const glyphOffset = (badgeSize - glyphSize) / 2;
  drawLoginGlyph(doc, badgeX + glyphOffset, badgeY + glyphOffset, glyphSize, primaryColor);

  // Nom de l'entreprise, à droite du logo. On réduit la taille du texte jusqu'à ce que le nom
  // rentre dans la place laissée par les coordonnées à droite, sinon un nom long passait
  // par-dessus l'adresse.
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

  // Coordonnées et infos légales belges (à droite)
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

/** Mention légale belge (raison sociale + n° TVA/BCE), numéro de page et bandeau du bas. */
const drawFooter = (doc, company, settings, page, pageCount) => {
  const pageHeight = doc.internal.pageSize.height;
  doc.setFontSize(8);
  doc.setTextColor(...secondaryColor);
  doc.setFont('helvetica', 'italic');

  const legalParts = [company.name];
  if (company.vat) legalParts.push(t('pdf.vatLine', { vat: company.vat }));
  const footerText = `${legalParts.join(' — ')} | ${settings.footerText || t('settings.footerText')}`;
  // Le texte du pied de page vient des Paramètres et peut être long : limité à 145 mm pour
  // ne pas passer sous le numéro de page, et seulement la première ligne, car le bas de page
  // n'a pas la place pour deux sans toucher le bandeau.
  doc.text(doc.splitTextToSize(footerText, 145)[0], 105, pageHeight - 15, { align: 'center' });

  doc.setFont('helvetica', 'normal');
  doc.text(t('pdf.pageLine', { page, pageCount }), 195, pageHeight - 15, { align: 'right' });

  doc.setFillColor(...primaryColor);
  doc.rect(0, pageHeight - 10, 210, 10, 'F');
};

/**
 * Pied de page ajouté sur *toutes* les pages une fois le document fini : on affiche le
 * nombre total de pages, qu'on ne connaît qu'à la fin.
 */
const stampFooters = (doc, company, settings) => {
  const pageCount = doc.internal.getNumberOfPages();
  for (let page = 1; page <= pageCount; page += 1) {
    doc.setPage(page);
    drawFooter(doc, company, settings, page, pageCount);
  }
};

/**
 * Réserve `needed` mm sous `y`, ajoute une page si on est trop près du pied de page, et
 * renvoie la position Y où écrire. Sans ça, un récapitulatif ou des notes en bas de page
 * s'écrivaient par-dessus le bandeau, voire en dehors de la feuille.
 */
const ensureSpace = (doc, company, y, needed) => {
  if (y + needed <= doc.internal.pageSize.height - FOOTER_ZONE) return y;
  doc.addPage();
  drawHeader(doc, company);
  return CONTENT_TOP;
};

/** Bloc « coordonnées bancaires » pour le paiement, sous le récapitulatif. */
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
    // La commande d'origine : la facture indique de quelle commande elle vient, comme l'avoir
    // indique de quelle facture il vient. Ça permet au client de relier les deux documents.
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
   * Adresse de facturation complète : sans code postal ni ville ce n'est pas une adresse, et
   * la raison sociale d'un client pro doit apparaître sur la facture. Le cadre s'adapte au
   * nombre de lignes au lieu d'avoir une hauteur fixe où les dernières lignes dépassaient.
   */
  const client = invoice.order?.client;
  // La police doit être choisie avant le calcul : `splitTextToSize` mesure avec la police
  // en cours, et une adresse longue doit passer à la ligne dans le cadre, pas en sortir.
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
    // Vente sans client : on l'écrit sur la facture plutôt que de laisser un cadre vide qui
    // ferait penser à un oubli.
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

  // Tableau des articles, sous le cadre client et le bloc de statut.
  const tableStartY = Math.max(120, clientBoxTop + clientBoxHeight + 10);

  const items = invoice.order?.items || [];

  // Total d'une ligne : celui calculé et enregistré par le backend (`OrderItem.totalPrice`),
  // remise de ligne déjà déduite. Si on recalculait quantité × prix, on aurait un brut qui ne
  // correspond pas au sous-total, et la facture ne tomberait plus juste pour le client.
  // Le calcul de secours ne sert que si totalPrice est absent.
  const lineNetTotal = (item) => Number(item.totalPrice ?? (item.quantity * item.unitPrice)) || 0;

  // La colonne « Remise » n'apparaît que si au moins une ligne a une remise : sinon on garde
  // la mise en page habituelle.
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
        // Padding horizontal réduit : à 5 mm de chaque côté, une colonne de 15 mm ne laissait
        // que 5 mm au texte et « Qté », « Remise », « Prix unitaire » passaient à la ligne.
        cellPadding: { top: 5, bottom: 5, left: 2.5, right: 2.5 }
      },
      /*
       * Le tableau est aligné sur les marges du document (15 → 195 mm), comme l'en-tête, le
       * titre et le récapitulatif. Avec la marge par défaut d'autoTable (40 pt ≈ 14,1 mm), il
       * commençait 1 mm trop à gauche et dépassait de 4 mm à droite (autoTable affichait un
       * warning dans la console).
       *
       * `top` place les pages suivantes sous le bandeau d'en-tête, `bottom` laisse la place
       * du pied de page : une ligne d'article ne doit jamais passer par-dessus.
       */
      margin: { top: CONTENT_TOP, left: 15, right: 15, bottom: FOOTER_ZONE },
      // L'en-tête est redessiné sur chaque page : une facture sur deux pages ne doit pas
      // avoir une deuxième page sans nom d'entreprise ni n° de TVA.
      didDrawPage: () => drawHeader(doc, company),
      // Quand la colonne « Remise » est ajoutée, on redistribue les largeurs en gardant le même
      // total (180 mm) : le tableau garde la même largeur, pas de dépassement.
      // Les largeurs tiennent l'intitulé le plus long des trois langues sur une ligne
      // (« Eenheidsprijs », « Discount », « Aant. »).
      columnStyles: hasLineDiscount
        ? {
            0: { cellWidth: 52 },
            1: { cellWidth: 28 },
            2: { cellWidth: 17, halign: 'center' },
            3: { cellWidth: 30, halign: 'right' },
            4: { cellWidth: 23, halign: 'right' },
            5: { cellWidth: 30, halign: 'right' }
          }
        : {
            0: { cellWidth: 68 },
            1: { cellWidth: 30 },
            2: { cellWidth: 18, halign: 'center' },
            3: { cellWidth: 32, halign: 'right' },
            4: { cellWidth: 32, halign: 'right' }
          }
    });
  }

  // Récapitulatif financier
  const finalY = doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10;

  // Cadre du récapitulatif
  doc.setDrawColor(...secondaryColor);
  doc.setLineWidth(0.5);

  const summaryX = 120;

  /*
   * Les montants sont repris tels quels de la facture enregistrée, le document ne recalcule
   * rien, même si la TVA est à zéro.
   *
   * Avant, quand le taux et le montant de TVA étaient à zéro, on recalculait avec le taux des
   * Paramètres, ce qui donnait une facture avec 21 % de plus que ce qui était en base : une
   * vente exonérée ou sans TVA sortait avec un total qu'on ne retrouvait ni dans l'écran
   * Factures ni ailleurs. Un document ne doit pas inventer son total : une facture sans TVA
   * se corrige en base, pas au moment de l'imprimer. On fait déjà pareil pour l'avoir.
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

  // On affiche toujours la TVA
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

  // Le récapitulatif n'est pas coupé : s'il ne rentre pas en entier en bas de la page, il
  // passe entièrement sur la suivante.
  const summaryHeight = financialSummary.reduce((height, item) => height + (item.bold ? 10 : 6), 0);
  let summaryY = ensureSpace(doc, company, finalY, summaryHeight);

  // Coordonnées bancaires (mention légale belge pour le paiement) : dans la colonne de gauche,
  // à côté des totaux, où il y a de la place. Quand elles étaient après les notes, elles
  // arrivaient tout en bas et passaient seules sur une deuxième page.
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

  // Mode de paiement
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
    // On descend d'autant de lignes que les notes en ont : sinon les coordonnées bancaires
    // s'écrivaient par-dessus dès que la note faisait deux lignes.
    summaryY += 6 + notesLines.length * 5;
  }

  stampFooters(doc, company, settings);

  // Génère le PDF
  const fileName = `${t('pdf.fileNamePrefix')}_${invoice.invoiceNumber}_${new Date().getTime()}.pdf`;
  doc.save(fileName);
};

/**
 * Avoir d'un retour client.
 *
 * C'est l'équivalent de la facture pour un retour : la vente a donné une facture, le retour
 * donne un avoir qui y fait référence. On y liste les articles rendus avec leur motif et
 * leur traitement (l'avoir doit dire *pourquoi* on rembourse), mais on ne met un montant que
 * sur les lignes remboursées : une remise en stock ou un échange rend la marchandise sans
 * rendre d'argent, donc la ligne apparaît sans montant.
 *
 * Les montants viennent directement du retour enregistré (`refundAmount`, calculé sur le prix
 * réellement payé) : le document ne recalcule rien, sinon il pourrait afficher un montant
 * différent de celui en base. Aucune TVA n'est ajoutée : le montant à rembourser est le
 * montant payé, tel quel.
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

  // Références : numéro de l'avoir, date, et documents de la vente concernée.
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

  // Infos du client
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
    // Vente sans client : on l'écrit sur l'avoir plutôt que de laisser un cadre vide qui
    // ferait penser à un oubli.
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
        // Sans remboursement, pas de montant : « 0,00 € » ferait penser à un article rendu
        // gratuitement, un tiret dit qu'il n'y a rien à rembourser.
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
        // Même padding horizontal réduit que la facture, pour que « Qté » / « Aant. » tienne
        // sur une ligne.
        cellPadding: { top: 4, bottom: 4, left: 2.5, right: 2.5 },
      },
      // Le tableau est aligné sur les marges du document (15 → 195 mm), comme le titre et le
      // récapitulatif : avec la marge par défaut d'autoTable, les six colonnes ne rentreraient
      // pas et les textes seraient coupés.
      // `top` place les pages suivantes sous le bandeau d'en-tête, `bottom` laisse la place du pied.
      margin: { top: CONTENT_TOP, left: 15, right: 15, bottom: FOOTER_ZONE },
      didDrawPage: () => drawHeader(doc, company),
      columnStyles: {
        0: { cellWidth: 54 },
        1: { cellWidth: 38 },
        2: { cellWidth: 38 },
        3: { cellWidth: 18, halign: 'center' },
        4: { cellWidth: 32, halign: 'right' },
      },
    });
  }

  // Récapitulatif : on rembourse ce que le client a payé, sans y rajouter de TVA. Le total
  // est le même que celui affiché à l'écran pour ce retour.
  const summaryX = 120;

  const subtotal = Number(stockReturn.refundAmount) || 0;

  const summary = [
    { label: t('pdf.creditNote.refundTotalLabel'), value: money(subtotal), bold: true, size: 12 },
  ];

  const summaryHeight = summary.reduce((height, item) => height + (item.bold ? 10 : 6), 0);
  const tableEnd = doc.lastAutoTable ? doc.lastAutoTable.finalY + 10 : tableStartY + 10;
  let summaryY = ensureSpace(doc, company, tableEnd, summaryHeight);
  // La quantité rendue va avec le montant : si le récapitulatif passe à la page suivante,
  // elle suit, sinon elle resterait toute seule en bas de la page d'avant.
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

  // Quantité rendue, à côté du montant : un avoir sans montant (remise en stock, échange)
  // prouve quand même que des articles ont été repris.
  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...secondaryColor);
  doc.text(
    t('pdf.creditNote.quantityLine', { count: stockReturn.totalQuantity ?? 0 }),
    15,
    quantityY
  );

  // Coordonnées bancaires : c'est par là que le remboursement sera fait. Comme sur la
  // facture, elles sont dans la colonne de gauche, à côté du récapitulatif.
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
