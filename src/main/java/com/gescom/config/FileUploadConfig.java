package com.gescom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.MultipartConfigElement;
import java.io.File;

@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads/images}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Créer le répertoire s'il n'existe pas
        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }

        // Ajouter le gestionnaire de ressources pour les images uploadées
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + uploadDirectory.getAbsolutePath() + "/");
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // Taille maximale du fichier : 5MB
        factory.setMaxFileSize(DataSize.ofMegabytes(5));
        
        // Taille maximale de la requête : 10MB
        factory.setMaxRequestSize(DataSize.ofMegabytes(10));
        
        // Seuil à partir duquel les fichiers sont écrits sur disque
        factory.setFileSizeThreshold(DataSize.ofKilobytes(1024));
        
        return factory.createMultipartConfig();
    }
}