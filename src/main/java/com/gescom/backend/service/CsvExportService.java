package com.gescom.backend.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Service générique d'export CSV. Le rowMapper passé en paramètre rend la méthode réutilisable
 * pour n'importe quel type de données (produits, commandes…). Détails d'interopérabilité Excel :
 * séparateur « ; », BOM UTF-8 en tête de fichier, et échappement des valeurs contenant des
 * caractères spéciaux (guillemets, retours ligne, séparateur).
 */
@Service
public class CsvExportService {

    /**
     * Export data to CSV format
     *
     * @param data          List of objects to export
     * @param headers       CSV column headers
     * @param rowMapper     Function to map each object to CSV row
     * @param <T>           Type of objects
     * @return CSV content as byte array
     */
    public <T> byte[] exportToCsv(List<T> data, String[] headers, Function<T, String[]> rowMapper) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {

            // Write BOM for Excel compatibility with UTF-8
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            // Write headers
            writer.println(String.join(";", headers));

            // Write data rows
            for (T item : data) {
                String[] row = rowMapper.apply(item);
                writer.println(String.join(";", escapeValues(row)));
            }

            writer.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error exporting to CSV", e);
        }
    }

    /**
     * Escape CSV values to handle special characters
     */
    private String[] escapeValues(String[] values) {
        String[] escaped = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i] != null ? values[i] : "";
            value = neutralizeFormula(value);
            // Escape quotes and wrap in quotes if contains special characters
            if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            escaped[i] = value;
        }
        return escaped;
    }

    /**
     * Neutralise l'injection de formule (CSV injection / DDE).
     *
     * Un tableur interprète comme une formule toute cellule débutant par =, +, -, @
     * ou une tabulation : un nom de client valant {@code =HYPERLINK(...)} ou
     * {@code =cmd|'/c calc'!A1} s'exécuterait à l'ouverture du fichier exporté.
     * Le remède recommandé (OWASP) est de préfixer ces cellules d'une apostrophe,
     * que le tableur retire à l'affichage : la valeur reste lisible mais cesse
     * d'être une formule. On applique le préfixe AVANT la mise entre guillemets,
     * pour qu'il se retrouve bien à l'intérieur de la cellule.
     */
    private String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    /**
     * Safe string conversion
     */
    public String toString(Object value) {
        return value != null ? value.toString() : "";
    }
}
