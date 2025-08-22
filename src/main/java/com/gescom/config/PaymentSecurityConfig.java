package com.gescom.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration de sécurité spécifique aux paiements externes
 * Implémente les meilleures pratiques de sécurité pour les transactions financières
 */
@Configuration
@RequiredArgsConstructor
public class PaymentSecurityConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Configuration de sécurité pour les endpoints de paiement public
     */
    @Bean
    @Order(1)
    public SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/payment/**")
            // Configuration CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Configuration CSRF - désactivé pour les webhooks mais protégé par signature
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/payment/webhook/**")
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
            )

            // Configuration des autorisations
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics de paiement
                .requestMatchers("/payment/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/webjars/**").permitAll()

                // API de statut de paiement (lecture seule)
                .requestMatchers("/api/payment/status/**").permitAll()

                // Webhooks (protégés par signature)
                .requestMatchers("/payment/webhook/**").permitAll()

                // Tout le reste nécessite une authentification
                .anyRequest().authenticated()
            )

            // Configuration des headers de sécurité
            .headers(headers -> headers
                // Protection contre le clickjacking
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)

                // Protection contre les attaques MIME
                .contentTypeOptions(contentTypeOptions -> {})

                // Protection XSS
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )

                // Politique de référent
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                // Headers personnalisés
                .addHeaderWriter((request, response) -> {
                    // Content Security Policy pour les paiements
                    response.setHeader("Content-Security-Policy",
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://js.stripe.com https://cdnjs.cloudflare.com; " +
                        "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                        "img-src 'self' data: https:; " +
                        "connect-src 'self' https://api.stripe.com; " +
                        "frame-src https://js.stripe.com; " +
                        "font-src 'self' https://cdnjs.cloudflare.com;"
                    );

                    // Permissions Policy
                    response.setHeader("Permissions-Policy",
                        "geolocation=(), microphone=(), camera=(), payment=(*)"
                    );

                    // Cache Control pour les pages de paiement
                    if (request.getRequestURI().startsWith("/payment/")) {
                        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        response.setHeader("Pragma", "no-cache");
                        response.setHeader("Expires", "0");
                    }
                })
            )

            // Configuration des sessions
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .sessionRegistry(sessionRegistry())
            )

            // Désactiver les fonctionnalités non nécessaires
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .build();
    }

    /**
     * Configuration CORS pour les paiements
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origines autorisées
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:8080", "https://js.stripe.com"));

        // Méthodes autorisées
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers autorisés
        configuration.setAllowedHeaders(List.of("*"));

        // Headers exposés
        configuration.setExposedHeaders(Arrays.asList(
            "X-Payment-Status",
            "X-Transaction-ID",
            "X-Payment-Reference"
        ));

        // Credentials autorisés
        configuration.setAllowCredentials(true);

        // Durée de mise en cache des précontrôles
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/payment/**", configuration);
        source.registerCorsConfiguration("/api/payment/**", configuration);

        return source;
    }

    /**
     * Configuration Redis pour la gestion des sessions et du rate limiting
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Utilisation de sérialiseurs string pour simplicité et performance
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.setDefaultSerializer(stringSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Registre des sessions pour le monitoring
     */
    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    /**
     * Gestionnaire de sessions concurrent
     */
    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }
}