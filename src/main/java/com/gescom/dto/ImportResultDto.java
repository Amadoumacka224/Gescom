package com.gescom.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImportResultDto {
    
    private int totalCount;
    private int importedCount;
    private int updatedCount;
    private int errorCount;
    private int skippedCount;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;
    
    private boolean dryRun;
    private List<ImportError> errors;
    private List<String> warnings;
    private String summary;
    
    // Constructeurs
    public ImportResultDto() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.startTime = LocalDateTime.now();
    }
    
    public ImportResultDto(boolean dryRun) {
        this();
        this.dryRun = dryRun;
    }
    
    // Getters et Setters
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getImportedCount() {
        return importedCount;
    }
    
    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }
    
    public int getUpdatedCount() {
        return updatedCount;
    }
    
    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }
    
    public int getErrorCount() {
        return errorCount;
    }
    
    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }
    
    public int getSkippedCount() {
        return skippedCount;
    }
    
    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public boolean isDryRun() {
        return dryRun;
    }
    
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
    
    public List<ImportError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<ImportError> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
        this.errorCount = this.errors.size();
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    // Méthodes utilitaires
    public void incrementImported() {
        this.importedCount++;
    }
    
    public void incrementUpdated() {
        this.updatedCount++;
    }
    
    public void incrementSkipped() {
        this.skippedCount++;
    }
    
    public void addError(int lineNumber, String field, String message) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(new ImportError(lineNumber, field, message));
        this.errorCount = this.errors.size();
    }
    
    public void addError(ImportError error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.errorCount = this.errors.size();
    }
    
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    public boolean isSuccessful() {
        return errorCount == 0 && (importedCount > 0 || updatedCount > 0);
    }
    
    public double getSuccessRate() {
        if (totalCount == 0) return 0.0;
        return ((double) (importedCount + updatedCount) / totalCount) * 100.0;
    }
    
    public void finish() {
        this.endTime = LocalDateTime.now();
        if (startTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
        generateSummary();
    }
    
    private void generateSummary() {
        if (dryRun) {
            this.summary = String.format("Mode test : %d articles seraient traités (%d importés, %d mis à jour, %d erreurs)",
                    totalCount, importedCount, updatedCount, errorCount);
        } else {
            this.summary = String.format("Import terminé : %d articles traités (%d importés, %d mis à jour, %d erreurs) en %d ms",
                    totalCount, importedCount, updatedCount, errorCount, durationMs);
        }
    }
    
    // Classe interne pour les erreurs
    public static class ImportError {
        private int lineNumber;
        private String field;
        private String message;
        private String value;
        
        public ImportError(int lineNumber, String field, String message) {
            this.lineNumber = lineNumber;
            this.field = field;
            this.message = message;
        }
        
        public ImportError(int lineNumber, String field, String message, String value) {
            this.lineNumber = lineNumber;
            this.field = field;
            this.message = message;
            this.value = value;
        }
        
        // Getters et Setters
        public int getLineNumber() {
            return lineNumber;
        }
        
        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
        
        public String getField() {
            return field;
        }
        
        public void setField(String field) {
            this.field = field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        @Override
        public String toString() {
            return String.format("Ligne %d - %s: %s%s", 
                    lineNumber, field, message, 
                    value != null ? " (valeur: '" + value + "')" : "");
        }
    }
}