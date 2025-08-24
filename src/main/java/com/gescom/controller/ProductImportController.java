package com.gescom.controller;

import com.gescom.dto.ImportResultDto;
import com.gescom.dto.ProductImportDto;
import com.gescom.entity.Product;
import com.gescom.service.ProductImportService;
import com.gescom.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/products")
public class ProductImportController {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportController.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Autowired
    private ProductImportService importService;

    @Autowired
    private ProductRepository productRepository;

    /**
     * Import de produits depuis un fichier Excel ou CSV
     */
    @PostMapping("/import")
    public String importProducts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting,
            @RequestParam(value = "skipInvalid", defaultValue = "true") boolean skipInvalid,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            RedirectAttributes redirectAttributes) {

        try {
            // Validation du fichier
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner un fichier.");
                return "redirect:/admin/settings";
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                redirectAttributes.addFlashAttribute("error", "Le fichier est trop volumineux (max 10MB).");
                return "redirect:/admin/settings";
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !isValidFileType(filename)) {
                redirectAttributes.addFlashAttribute("error", "Format de fichier non supporté. Utilisez .xlsx, .xls ou .csv");
                return "redirect:/admin/settings";
            }

            // Traitement du fichier
            List<ProductImportDto> products = parseFile(file);
            
            if (products.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Aucun produit trouvé dans le fichier ou format incorrect.");
                return "redirect:/admin/settings";
            }

            // Import des produits
            ImportResultDto result = importService.importProducts(products, updateExisting, skipInvalid, dryRun);
            
            // Messages de résultat
            if (dryRun) {
                redirectAttributes.addFlashAttribute("success", 
                    String.format("Mode test : %d produits seraient importés, %d mis à jour, %d erreurs.", 
                    result.getImportedCount(), result.getUpdatedCount(), result.getErrorCount()));
            } else {
                redirectAttributes.addFlashAttribute("success", 
                    String.format("Import terminé : %d produits importés, %d mis à jour, %d erreurs.", 
                    result.getImportedCount(), result.getUpdatedCount(), result.getErrorCount()));
            }

            if (!result.getErrors().isEmpty()) {
                redirectAttributes.addFlashAttribute("importErrors", result.getErrors());
            }

            logger.info("Import de produits terminé: {} importés, {} mis à jour, {} erreurs", 
                result.getImportedCount(), result.getUpdatedCount(), result.getErrorCount());

        } catch (Exception e) {
            logger.error("Erreur lors de l'import de produits: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'import: " + e.getMessage());
        }

        return "redirect:/admin/settings";
    }

    /**
     * Aperçu du fichier avant import
     */
    @PostMapping("/import/preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewImport(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Fichier vide");
                return ResponseEntity.badRequest().body(response);
            }

            List<ProductImportDto> products = parseFile(file);
            
            response.put("success", true);
            response.put("totalRows", products.size());
            response.put("preview", products.stream().limit(10).collect(Collectors.toList()));
            response.put("hasMore", products.size() > 10);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Erreur lors de l'aperçu: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Export des produits en Excel
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportProductsExcel() throws IOException {
        List<Product> products = productRepository.findAll();
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Articles");
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "Nom", "Description", "Prix", "Unité", "Référence", "Catégorie", "Fournisseur"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(createHeaderStyle(workbook));
        }
        
        // Données
        int rowNum = 1;
        for (Product product : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(product.getId());
            row.createCell(1).setCellValue(product.getName());
            row.createCell(2).setCellValue(product.getDescription() != null ? product.getDescription() : "");
            row.createCell(3).setCellValue(product.getPriceIncludingVat() != null ? product.getPriceIncludingVat().doubleValue() : 0.0);
            row.createCell(4).setCellValue(product.getUnit() != null ? product.getUnit().toString() : "");
            row.createCell(5).setCellValue(product.getReference() != null ? product.getReference() : "");
            // row.createCell(6).setCellValue(product.getCategory() != null ? product.getCategory().getName() : "");
            // row.createCell(7).setCellValue(product.getSupplier() != null ? product.getSupplier().getName() : "");
        }
        
        // Auto-resize des colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "articles.xlsx");
        
        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(outputStream.toByteArray());
    }

    /**
     * Export des produits en CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportProductsCsv() {
        List<Product> products = productRepository.findAll();
        
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nom,Description,Prix,Unité,Référence,Catégorie,Fournisseur\n");
        
        for (Product product : products) {
            csv.append(String.format("%d,\"%s\",\"%s\",%.2f,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                product.getId(),
                escapeCsv(product.getName()),
                escapeCsv(product.getDescription()),
                product.getPriceIncludingVat() != null ? product.getPriceIncludingVat().doubleValue() : 0.0,
                product.getUnit() != null ? escapeCsv(product.getUnit().toString()) : "",
                escapeCsv(product.getReference()),
                "", // product.getCategory() != null ? escapeCsv(product.getCategory().getName()) : "",
                ""  // product.getSupplier() != null ? escapeCsv(product.getSupplier().getName()) : ""
            ));
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "articles.csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
    }

    /**
     * Télécharger le modèle Excel
     */
    @GetMapping("/template/excel")
    public ResponseEntity<byte[]> downloadExcelTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Modèle Articles");
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        String[] headers = {"nom", "description", "prix", "unite", "reference", "categorie", "fournisseur"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(createHeaderStyle(workbook));
        }
        
        // Exemples
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("Exemple Article");
        exampleRow.createCell(1).setCellValue("Description de l'article");
        exampleRow.createCell(2).setCellValue(19.99);
        exampleRow.createCell(3).setCellValue("pièce");
        exampleRow.createCell(4).setCellValue("REF001");
        exampleRow.createCell(5).setCellValue("Électronique");
        exampleRow.createCell(6).setCellValue("Fournisseur A");
        
        // Auto-resize
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "modele-articles.xlsx");
        
        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(outputStream.toByteArray());
    }

    /**
     * Télécharger le modèle CSV
     */
    @GetMapping("/template/csv")
    public ResponseEntity<String> downloadCsvTemplate() {
        StringBuilder csv = new StringBuilder();
        csv.append("nom,description,prix,unite,reference,categorie,fournisseur\n");
        csv.append("Exemple Article,Description de l'article,19.99,pièce,REF001,Électronique,Fournisseur A\n");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "modele-articles.csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
    }

    /**
     * Parse un fichier Excel ou CSV en liste de ProductImportDto
     */
    private List<ProductImportDto> parseFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) return new ArrayList<>();

        if (filename.toLowerCase().endsWith(".csv")) {
            return parseCsvFile(file);
        } else if (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls")) {
            return parseExcelFile(file);
        }
        
        throw new IllegalArgumentException("Format de fichier non supporté: " + filename);
    }

    /**
     * Parse un fichier CSV
     */
    private List<ProductImportDto> parseCsvFile(MultipartFile file) throws IOException {
        List<ProductImportDto> products = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                
                try {
                    String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split CSV en respectant les guillemets
                    ProductImportDto product = parseCsvLine(values, lineNumber);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    logger.warn("Erreur ligne {}: {}", lineNumber, e.getMessage());
                }
            }
        }
        
        return products;
    }

    /**
     * Parse un fichier Excel
     */
    private List<ProductImportDto> parseExcelFile(MultipartFile file) throws IOException {
        List<ProductImportDto> products = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // Skip header
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                
                try {
                    ProductImportDto product = parseExcelRow(row, rowIndex + 1);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    logger.warn("Erreur ligne {}: {}", rowIndex + 1, e.getMessage());
                }
            }
        }
        
        return products;
    }

    /**
     * Parse une ligne CSV
     */
    private ProductImportDto parseCsvLine(String[] values, int lineNumber) {
        if (values.length < 3) return null; // Au minimum nom, prix, quantité
        
        ProductImportDto product = new ProductImportDto();
        product.setLineNumber(lineNumber);
        product.setName(cleanCsvValue(values[0]));
        product.setDescription(values.length > 1 ? cleanCsvValue(values[1]) : "");
        product.setPrice(parseDecimal(cleanCsvValue(values[2])));
        product.setQuantity(parseInt(cleanCsvValue(values[3])));
        product.setReference(values.length > 4 ? cleanCsvValue(values[4]) : "");
        product.setCategory(values.length > 5 ? cleanCsvValue(values[5]) : "");
        product.setSupplier(values.length > 6 ? cleanCsvValue(values[6]) : "");
        
        return product;
    }

    /**
     * Parse une ligne Excel
     */
    private ProductImportDto parseExcelRow(Row row, int lineNumber) {
        ProductImportDto product = new ProductImportDto();
        product.setLineNumber(lineNumber);
        product.setName(getCellStringValue(row.getCell(0)));
        product.setDescription(getCellStringValue(row.getCell(1)));
        product.setPrice(getCellDecimalValue(row.getCell(2)));
        product.setQuantity(getCellIntValue(row.getCell(3)));
        product.setReference(getCellStringValue(row.getCell(4)));
        product.setCategory(getCellStringValue(row.getCell(5)));
        product.setSupplier(getCellStringValue(row.getCell(6)));
        
        return product.getName() != null && !product.getName().trim().isEmpty() ? product : null;
    }

    // Méthodes utilitaires
    private boolean isValidFileType(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv");
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private String cleanCsvValue(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\"\"", "\"");
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private BigDecimal getCellDecimalValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                try {
                    return new BigDecimal(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            default:
                return BigDecimal.ZERO;
        }
    }

    private Integer getCellIntValue(Cell cell) {
        if (cell == null) return 0;
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                try {
                    return Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}