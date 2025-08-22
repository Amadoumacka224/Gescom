package com.gescom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service de sécurité pour les paiements externes
 * Gestion de la protection contre les attaques et de la conformité sécuritaire
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${security.payment.max-attempts-per-ip:50}")
    private int maxAttemptsPerIp;

    @Value("${security.payment.rate-limit-window:300}")
    private int rateLimitWindowSeconds;

    @Value("${security.payment.suspicious-threshold:100}")
    private int suspiciousThreshold;

    private static final String RATE_LIMIT_PREFIX = "payment_rate_limit:";
    private static final String FAILED_ATTEMPTS_PREFIX = "payment_failed:";
    private static final String SUSPICIOUS_IP_PREFIX = "suspicious_ip:";

    /**
     * Valide une requête client pour sécurité
     */
    public void validateClientRequest(String clientIp, String userAgent) {
        log.debug("Validation requête client - IP: {}, User-Agent: {}", clientIp, userAgent);
        log.info("DEBUG - IP reçue: '{}' (Type: {})", clientIp, clientIp != null ? clientIp.getClass().getSimpleName() : "null");
        
        // Vérification de l'IP
        if (clientIp == null || clientIp.trim().isEmpty()) {
            throw new SecurityException("Adresse IP manquante");
        }
        
        // Vérification de l'User-Agent
        if (userAgent == null || userAgent.trim().isEmpty()) {
            throw new SecurityException("User-Agent manquant");
        }
        
        // Validation du format IP
        if (!isValidIpAddress(clientIp)) {
            log.warn("IP invalide détectée: '{}' - Validation échouée", clientIp);
            throw new SecurityException("Format d'adresse IP invalide");
        }
        
        log.debug("IP validée avec succès: {}", clientIp);
        
        // Vérification des tentatives suspectes
        if (isSuspiciousIp(clientIp)) {
            log.warn("Tentative de paiement depuis une IP suspecte: {}", clientIp);
            throw new SecurityException("Adresse IP bloquée pour activité suspecte");
        }
        
        // Vérification du rate limiting
        if (isRateLimited(clientIp)) {
            log.warn("Rate limit dépassé pour IP: {}", clientIp);
            throw new SecurityException("Trop de tentatives, veuillez réessayer plus tard");
        }
        
        // Enregistrement de la tentative
        recordAttempt(clientIp);
    }

    /**
     * Enregistre une tentative de paiement échouée
     */
    public void recordFailedPaymentAttempt(String clientIp, String reason) {
        log.warn("Échec paiement enregistré - IP: {}, Raison: {}", clientIp, reason);
        
        String key = FAILED_ATTEMPTS_PREFIX + clientIp;
        String attempts = redisTemplate.opsForValue().get(key);
        
        int failedCount = attempts != null ? Integer.parseInt(attempts) : 0;
        failedCount++;
        
        redisTemplate.opsForValue().set(key, String.valueOf(failedCount), 
                Duration.ofSeconds(rateLimitWindowSeconds));
        
        // Marquer comme suspect si trop d'échecs
        if (failedCount >= suspiciousThreshold) {
            markAsSuspicious(clientIp, "Trop de tentatives échouées: " + failedCount);
        }
    }

    /**
     * Réinitialise les compteurs de sécurité pour une IP (développement uniquement)
     */
    public void resetSecurityCounters(String clientIp) {
        log.info("Réinitialisation des compteurs de sécurité pour IP: {}", clientIp);
        
        // Supprimer rate limit
        redisTemplate.delete(RATE_LIMIT_PREFIX + clientIp);
        
        // Supprimer tentatives échouées
        redisTemplate.delete(FAILED_ATTEMPTS_PREFIX + clientIp);
        
        // Supprimer marquage suspect
        redisTemplate.delete(SUSPICIOUS_IP_PREFIX + clientIp);
    }

    /**
     * Enregistre un paiement réussi
     */
    public void recordSuccessfulPayment(String clientIp) {
        log.info("Paiement réussi enregistré pour IP: {}", clientIp);
        
        // Réinitialiser les compteurs d'échec pour cette IP
        redisTemplate.delete(FAILED_ATTEMPTS_PREFIX + clientIp);
    }

    /**
     * Marque une IP comme suspecte
     */
    public void markAsSuspicious(String clientIp, String reason) {
        log.warn("Marquage IP suspecte: {} - Raison: {}", clientIp, reason);
        
        String key = SUSPICIOUS_IP_PREFIX + clientIp;
        String value = reason + "|" + LocalDateTime.now().toString();
        
        // Bloquer pendant 24 heures
        redisTemplate.opsForValue().set(key, value, Duration.ofHours(24));
    }

    /**
     * Vérifie si une IP est suspecte
     */
    public boolean isSuspiciousIp(String clientIp) {
        String key = SUSPICIOUS_IP_PREFIX + clientIp;
        return redisTemplate.hasKey(key);
    }

    /**
     * Vérifie le rate limiting
     */
    private boolean isRateLimited(String clientIp) {
        String key = RATE_LIMIT_PREFIX + clientIp;
        String attempts = redisTemplate.opsForValue().get(key);
        
        if (attempts == null) {
            return false;
        }
        
        return Integer.parseInt(attempts) >= maxAttemptsPerIp;
    }

    /**
     * Enregistre une tentative
     */
    private void recordAttempt(String clientIp) {
        String key = RATE_LIMIT_PREFIX + clientIp;
        String attempts = redisTemplate.opsForValue().get(key);
        
        int count = attempts != null ? Integer.parseInt(attempts) : 0;
        count++;
        
        redisTemplate.opsForValue().set(key, String.valueOf(count), 
                Duration.ofSeconds(rateLimitWindowSeconds));
    }

    /**
     * Valide le format d'une adresse IP (IPv4 et IPv6)
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        // Cas spéciaux pour localhost et développement
        if ("localhost".equals(ip) || "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        
        // Validation IPv4
        if (isValidIPv4(ip)) {
            return true;
        }
        
        // Validation IPv6 basique
        if (isValidIPv6(ip)) {
            return true;
        }
        
        return false;
    }
    
    private boolean isValidIPv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isValidIPv6(String ip) {
        // Validation basique IPv6
        return ip.contains(":") && ip.length() >= 2 && ip.length() <= 39;
    }

    /**
     * Génère un hash sécurisé pour les données sensibles
     */
    public String generateSecureHash(String data, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hash = md.digest(data.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Erreur lors de la génération du hash", e);
            throw new RuntimeException("Impossible de générer le hash sécurisé", e);
        }
    }

    /**
     * Valide un token de sécurité
     */
    public boolean validateSecurityToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // Validation de la longueur et du format
        if (token.length() < 32 || token.length() > 64) {
            return false;
        }
        
        // Validation des caractères (Base64 URL-safe)
        return token.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Nettoie les données de sécurité expirées
     */
    public void cleanupExpiredSecurityData() {
        log.info("Nettoyage des données de sécurité expirées");
        
        // Le nettoyage automatique est géré par les TTL Redis
        // Cette méthode peut être utilisée pour un nettoyage manuel si nécessaire
    }

    /**
     * Obtient les statistiques de sécurité
     */
    public SecurityStatistics getSecurityStatistics() {
        // Compter les IPs suspectes actives
        int suspiciousIpCount = 0;
        
        // Dans une vraie implémentation, utiliser SCAN pour compter
        // les clés avec le préfixe SUSPICIOUS_IP_PREFIX
        
        return new SecurityStatistics(suspiciousIpCount, maxAttemptsPerIp, rateLimitWindowSeconds);
    }

    /**
     * Débloque une IP suspecte (pour l'administration)
     */
    public void unblockSuspiciousIp(String clientIp, String adminReason) {
        log.info("Déblocage IP suspecte: {} par admin - Raison: {}", clientIp, adminReason);
        
        redisTemplate.delete(SUSPICIOUS_IP_PREFIX + clientIp);
        redisTemplate.delete(FAILED_ATTEMPTS_PREFIX + clientIp);
        redisTemplate.delete(RATE_LIMIT_PREFIX + clientIp);
    }

    /**
     * Classe pour les statistiques de sécurité
     */
    public record SecurityStatistics(
            int suspiciousIpCount,
            int maxAttemptsPerIp,
            int rateLimitWindowSeconds
    ) {}
}