package com.gescom.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que l'export CSV bloque l'injection de formule (CSV injection / DDE) : une valeur
 * qui commence par =, +, -, @ ou une tabulation ne doit plus être lue comme une formule
 * quand on ouvre le fichier dans un tableur. Test unitaire simple, sans contexte Spring.
 */
class CsvExportServiceTest {

    private final CsvExportService service = new CsvExportService();

    /** Lit le CSV généré en sautant le BOM UTF-8 du début, et renvoie la ligne de données. */
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
        // =HYPERLINK(...) est l'exemple classique pour faire fuiter des données.
        String row = firstDataRow(exportSingleCell("=HYPERLINK(\"http://evil.tld\")"));
        // L'apostrophe est bien ajoutée, et comme la valeur contient un guillemet elle est aussi
        // entourée de guillemets (l'apostrophe reste à l'intérieur).
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
        // Un signe - au milieu (numéro de TVA, référence) ne fait pas une formule.
        assertThat(firstDataRow(exportSingleCell("BE-0123-456"))).isEqualTo("BE-0123-456");
    }
}
