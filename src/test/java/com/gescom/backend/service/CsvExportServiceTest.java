package com.gescom.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'export CSV neutralise l'injection de formule (CSV injection / DDE) :
 * une valeur débutant par =, +, -, @ ou une tabulation ne doit plus être interprétée
 * comme une formule à l'ouverture dans un tableur. Test unitaire pur, sans contexte Spring.
 */
class CsvExportServiceTest {

    private final CsvExportService service = new CsvExportService();

    /** Décode le CSV produit en sautant le BOM UTF-8 de tête, et rend la ligne de données. */
    private String firstDataRow(byte[] csv) {
        String content = new String(csv, StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        // Ligne 0 = en-têtes, ligne 1 = première ligne de données.
        return content.split("\\R")[1];
    }

    private byte[] exportSingleCell(String value) {
        return service.exportToCsv(
                List.of(value),
                new String[]{"valeur"},
                v -> new String[]{v});
    }

    @Test
    void prefixesFormulaLeadingEquals() {
        // =HYPERLINK(...) est le cas d'exfiltration classique.
        String row = firstDataRow(exportSingleCell("=HYPERLINK(\"http://evil.tld\")"));
        // L'apostrophe de neutralisation est présente, et comme la valeur contient un
        // guillemet elle est aussi mise entre guillemets — l'apostrophe reste à l'intérieur.
        assertThat(row).startsWith("\"'=HYPERLINK");
    }

    @Test
    void prefixesFormulaLeadingPlusMinusAtAndTab() {
        assertThat(firstDataRow(exportSingleCell("+1+1"))).isEqualTo("'+1+1");
        assertThat(firstDataRow(exportSingleCell("-2+3"))).isEqualTo("'-2+3");
        assertThat(firstDataRow(exportSingleCell("@SUM(A1:A2)"))).isEqualTo("'@SUM(A1:A2)");
        assertThat(firstDataRow(exportSingleCell("\tvaleur"))).isEqualTo("'\tvaleur");
    }

    @Test
    void leavesBenignValuesUntouched() {
        assertThat(firstDataRow(exportSingleCell("Dupont & Fils"))).isEqualTo("Dupont & Fils");
        assertThat(firstDataRow(exportSingleCell("largeur 30 cm"))).isEqualTo("largeur 30 cm");
    }

    @Test
    void doesNotFlagNumberContainingButNotStartingWithOperator() {
        // Un signe - au milieu (numéro de TVA, référence) n'est pas une formule.
        assertThat(firstDataRow(exportSingleCell("BE-0123-456"))).isEqualTo("BE-0123-456");
    }
}
