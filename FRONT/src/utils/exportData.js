/**
 * Exports de tableaux (CSV pour Excel, PDF), communs à toutes les pages.
 *
 * Avant, chaque page faisait son CSV à la main en collant les valeurs avec des virgules.
 * Ça posait trois problèmes, sans aucun message d'erreur :
 *   - pas d'échappement : une raison sociale avec une virgule (« Dupont, SPRL ») ou un retour
 *     à la ligne décalait toutes les colonnes suivantes ;
 *   - pas de BOM : Excel lit alors le fichier en ANSI et affiche « CrÃ©Ã© » au lieu de « Créé » ;
 *   - virgule comme séparateur : Excel en français attend un point-virgule et met toute la
 *     ligne dans la première colonne.
 *
 * Les mêmes colonnes servent pour les deux formats, donc on exporte exactement ce qui est
 * affiché. L'ancien export des rapports lisait d'autres champs que le tableau
 * (`totalAmount` au lieu de `finalAmount`, `orderDate` au lieu de `createdAt`).
 *
 * Une colonne est définie par `{ header, value, align }`, `value` reçoit la ligne.
 */

import i18n from '../i18n';

/*
 * jspdf n'est chargé qu'au premier export PDF (voir le commentaire dans `pdfGenerator.js`,
 * c'est le même principe).
 *
 * `exportToCsv` reste synchrone exprès : il ne fait que du texte et n'utilise aucune
 * librairie. Pas de raison de lui faire attendre 400 ko de chargement.
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
        pdfLibsPromise = undefined;
        throw error;
      });
  }
  return pdfLibsPromise;
};

/** Séparateur attendu par Excel en français. */
const CSV_SEPARATOR = ';';

/** BOM : sans lui, Excel n'ouvre pas le fichier en UTF-8. */
const BOM = '﻿';

/**
 * Échappe une cellule CSV (RFC 4180) : on double les guillemets, et on entoure le champ de
 * guillemets s'il contient un séparateur, un guillemet ou un retour à la ligne.
 */
const escapeCsvCell = (raw) => {
  const value = raw == null ? '' : String(raw);
  if (value.includes('"') || value.includes(CSV_SEPARATOR) || /[\r\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
};

/** Nom de fichier avec la date : « rapport-ventes-2026-07-28.csv ». */
const stampedName = (base, extension) => {
  const today = new Date();
  const offsetMs = today.getTimezoneOffset() * 60 * 1000;
  const stamp = new Date(today.getTime() - offsetMs).toISOString().split('T')[0];
  return `${base}-${stamp}.${extension}`;
};

const triggerDownload = (blob, filename) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // Si on ne révoque pas l'URL, le blob reste en mémoire jusqu'au rechargement de la page.
  URL.revokeObjectURL(url);
};

/**
 * Exporte des lignes dans un fichier CSV à télécharger.
 * @param {object} options
 * @param {string} options.filename  nom de base, sans date ni extension
 * @param {Array<{header: string, value: Function}>} options.columns
 * @param {Array<object>} options.rows
 */
export const exportToCsv = ({ filename, columns, rows }) => {
  const lines = [
    columns.map((column) => escapeCsvCell(column.header)).join(CSV_SEPARATOR),
    ...rows.map((row) =>
      columns.map((column) => escapeCsvCell(column.value(row))).join(CSV_SEPARATOR)
    ),
  ];

  // CRLF : fin de ligne attendue par Excel sous Windows.
  const blob = new Blob([BOM + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
  triggerDownload(blob, stampedName(filename, 'csv'));
};

/** Couleurs de la charte (voir `tailwind.config.js`), comme pour la facture PDF. */
const PRIMARY_COLOR = [31, 119, 180]; // #1F77B4 — primary-600
const DARK_COLOR = [26, 39, 64]; // #1A2740 — gray-900
const MUTED_COLOR = [100, 118, 151]; // #647697 — gray-500

/**
 * Exporte des lignes en PDF paysage, avec un en-tête et un pied de page numéroté.
 *
 * @param {object} options
 * @param {string} options.filename    nom de base, sans date ni extension
 * @param {string} options.title       titre du rapport
 * @param {string} [options.subtitle]  période et filtres appliqués
 * @param {Array<{label: string, value: string}>} [options.summary] chiffres affichés en haut
 * @param {Array<{header: string, value: Function, align?: string}>} options.columns
 * @param {Array<object>} options.rows
 */
export const exportToPdf = async ({ filename, title, subtitle, summary = [], columns, rows }) => {
  await loadPdfLibs();
  // Paysage : ces rapports ont 6 ou 7 colonnes, illisibles en portrait.
  const doc = new jsPDF({ orientation: 'landscape' });
  const pageWidth = doc.internal.pageSize.getWidth();

  doc.setFillColor(...PRIMARY_COLOR);
  doc.rect(0, 0, pageWidth, 26, 'F');

  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(15);
  doc.text(title, 14, 12);

  if (subtitle) {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.text(subtitle, 14, 19);
  }

  doc.setFontSize(8);
  doc.text(
    i18n.t('export.generatedOn', { date: new Date().toLocaleString(i18n.t('export.locale')) }),
    pageWidth - 14,
    12,
    { align: 'right' }
  );

  let cursorY = 34;

  // Bandeau de chiffres : on retrouve dans le PDF les mêmes totaux qu'au-dessus du tableau
  // à l'écran.
  if (summary.length > 0) {
    doc.setFontSize(9);
    const columnWidth = (pageWidth - 28) / summary.length;
    summary.forEach((item, index) => {
      const x = 14 + index * columnWidth;
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(...MUTED_COLOR);
      doc.text(item.label, x, cursorY);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(...DARK_COLOR);
      doc.setFontSize(12);
      doc.text(String(item.value), x, cursorY + 6);
      doc.setFontSize(9);
    });
    cursorY += 14;
  }

  autoTable(doc, {
    startY: cursorY,
    head: [columns.map((column) => column.header)],
    body: rows.map((row) => columns.map((column) => String(column.value(row) ?? ''))),
    styles: { fontSize: 8, cellPadding: 2.5, overflow: 'linebreak' },
    headStyles: { fillColor: PRIMARY_COLOR, textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [244, 247, 252] },
    columnStyles: columns.reduce((styles, column, index) => {
      if (column.align) styles[index] = { halign: column.align };
      return styles;
    }, {}),
    margin: { left: 14, right: 14 },
    didDrawPage: (data) => {
      const pageHeight = doc.internal.pageSize.getHeight();
      doc.setFontSize(8);
      doc.setTextColor(...MUTED_COLOR);
      doc.text(
        i18n.t('export.page', { page: doc.internal.getNumberOfPages() }),
        pageWidth - data.settings.margin.right,
        pageHeight - 8,
        { align: 'right' }
      );
      doc.text(
        i18n.t('export.rowCount', { count: rows.length }),
        data.settings.margin.left,
        pageHeight - 8
      );
    },
  });

  doc.save(stampedName(filename, 'pdf'));
};
