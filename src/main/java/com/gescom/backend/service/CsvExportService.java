package com.gescom.backend.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Export CSV générique. Grâce au rowMapper passé en paramètre, la méthode marche pour
 * n'importe quel type de données (produits, commandes…). Pour Excel : séparateur « ; »,
 * BOM UTF-8 au début du fichier, et échappement des valeurs qui contiennent des caractères
 * spéciaux (guillemets, retours à la ligne, séparateur).
 */
@Service
public class CsvExportService {

    /**
     * Exporte des données au format CSV
     *
     * @param data          liste des objets à exporter
     * @param headers       en-têtes des colonnes
     * @param rowMapper     fonction qui transforme un objet en ligne CSV
     * @param <T>           type des objets
     * @return le contenu CSV en tableau d'octets
     */
    public <T> byte[] exportToCsv(List<T> data, String[] headers, Function<T, String[]> rowMapper) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {

            // BOM pour qu'Excel lise bien l'UTF-8
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            // En-têtes
            writer.println(String.join(";", headers));

            // Lignes de données
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
     * Échappe une valeur CSV qui contient des caractères spéciaux
     */
    private String[] escapeValues(String[] values) {
        String[] escaped = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i] != null ? values[i] : "";
            value = neutralizeFormula(value);
            // Double les guillemets et entoure de guillemets si caractères spéciaux
            if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            escaped[i] = value;
        }
        return escaped;
    }

    /**
     * Protection contre l'injection de formule (CSV injection / DDE).
     *
     * Un tableur considère comme une formule toute cellule qui commence par =, +, -, @ ou
     * une tabulation : un nom de client comme {@code =HYPERLINK(...)} ou
     * {@code =cmd|'/c calc'!A1} serait exécuté à l'ouverture du fichier.
     * La solution conseillée par l'OWASP est d'ajouter une apostrophe devant, que le tableur
     * n'affiche pas : la valeur reste lisible mais n'est plus une formule. On ajoute
     * l'apostrophe AVANT les guillemets, pour qu'elle soit bien dans la cellule.
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
     * Conversion en String sans risque de null
     */
    public String toString(Object value) {
        return value != null ? value.toString() : "";
    }
}
