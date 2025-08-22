package com.gescom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ImageService {

    @Value("${app.upload.dir:uploads/images}")
    private String uploadDir;

    @Value("${app.upload.url:/uploads/images}")
    private String uploadUrl;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp"};

    /**
     * Sauvegarde une image uploadée
     */
    public String saveImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }

        // Vérifier la taille
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Le fichier est trop volumineux (max 5MB)");
        }

        // Vérifier l'extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }

        String extension = getFileExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("Type de fichier non autorisé. Utilisez: JPG, PNG, GIF, WebP");
        }

        // Créer le répertoire de destination s'il n'existe pas
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Générer un nom unique
        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename);

        // Copier le fichier
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Retourner l'URL relative
        return uploadUrl + "/" + filename;
    }

    /**
     * Supprime une image
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            // Extraire le nom du fichier de l'URL
            if (imageUrl.startsWith(uploadUrl)) {
                String filename = imageUrl.substring(uploadUrl.length() + 1);
                Path filePath = Paths.get(uploadDir).resolve(filename);
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // Log l'erreur mais ne pas faire échouer l'opération
            System.err.println("Erreur lors de la suppression de l'image: " + e.getMessage());
        }
    }

    /**
     * Vérifie si l'extension est autorisée
     */
    private boolean isAllowedExtension(String extension) {
        if (extension == null) {
            return false;
        }
        
        String ext = extension.toLowerCase();
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extrait l'extension du nom de fichier
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    /**
     * Valide une URL d'image
     */
    public boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            // Vérification basique de l'URL
            String lowerUrl = url.toLowerCase();
            return lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") || lowerUrl.startsWith("/");
        } catch (Exception e) {
            return false;
        }
    }
}