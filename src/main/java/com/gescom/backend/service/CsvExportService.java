package com.gescom.backend.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

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
            // Escape quotes and wrap in quotes if contains special characters
            if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            escaped[i] = value;
        }
        return escaped;
    }

    /**
     * Safe string conversion
     */
    public String toString(Object value) {
        return value != null ? value.toString() : "";
    }
}
