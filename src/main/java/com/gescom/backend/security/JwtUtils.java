package com.gescom.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Boîte à outils JWT : génération, lecture et validation des tokens d'authentification.
 * Le secret et la durée de validité sont injectés depuis application.properties
 * (jwt.secret / jwt.expiration) pour ne pas figer ces valeurs sensibles dans le code.
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    // Secret partagé servant à signer ET vérifier les tokens (algorithme HMAC symétrique).
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Durée de vie d'un token en millisecondes.
    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /** Dérive la clé de signature HMAC à partir du secret configuré. */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Émet un token signé pour un utilisateur déjà authentifié (utilisé après le login). */
    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /** Extrait le nom d'utilisateur (subject) contenu dans un token valide. */
    public String getUsernameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Vérifie la signature et la validité d'un token. Chaque cause d'échec
     * (signature falsifiée, token expiré, format invalide…) est tracée distinctement
     * pour faciliter le diagnostic, mais renvoie toujours false sans propager d'exception.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException e) {
            log.warn("Signature JWT invalide (token émis avec un autre secret): {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Token JWT invalide: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Token JWT expiré: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Token JWT non supporté: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Claims JWT vides: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Token JWT rejeté: {}", e.getMessage());
        }
        return false;
    }
}
