package com.gescom.backend.config;

import com.gescom.backend.security.CustomUserDetailsService;
import com.gescom.backend.security.JwtAuthenticationEntryPoint;
import com.gescom.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration de la sécurité de l'application.
 * - Active Spring Security et les annotations de sécurité sur les méthodes.
 * - Définit les beans nécessaires pour l'authentification JWT, CORS et l'encodage des mots de passe.
 */

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // Service qui charge les détails de l'utilisateur (username, password, roles).

    @Autowired
    private CustomUserDetailsService userDetailsService;

    // Point d'entrée pour gérer les erreurs d'authentification (ex: token invalide).
    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    // Origines autorisées pour les requêtes CORS, définies dans application.properties.
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Bean pour le filtre d'authentification JWT.
     * Intercepte les requêtes pour extraire et valider le token JWT.
     */

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {

        return new JwtAuthenticationFilter();
    }

    /**
     * Configure le provider d'authentification DAO.
     * Utilise le CustomUserDetailsService pour charger les utilisateurs
     * et le PasswordEncoder pour vérifier les mots de passe.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Expose l'AuthenticationManager afin de pouvoir l'injecter ailleurs (ex: contrôleur d'authentification).
     * Récupéré depuis la configuration d'authentification fournie par Spring.
     */

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }



    /**
     * Bean pour l'encodage des mots de passe.
     * BCrypt est utilisé ici — algorithme sécurisé recommandé pour les mots de passe.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuration principale de la sécurité HTTP.
     * - Désactive CSRF (non nécessaire pour les API REST stateless).
     * - Gère les exceptions d'authentification avec un point d'entrée personnalisé.
     * - Définit la politique de session sur STATELESS (pas de sessions côté serveur).
     * - Configure les règles d'autorisation des requêtes.
     * - Active CORS avec une configuration personnalisée.
     */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth.requestMatchers("/api/auth/**", "/api/init/test").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        // Enregistre le provider DAO (UserDetailsService + PasswordEncoder)
        http.authenticationProvider(authenticationProvider());
        // Ajoute le filtre JWT avant le filtre d'authentification standard
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuration CORS centralisée.
     * - Lit les origines autorisées depuis la propriété `cors.allowed-origins` (séparées par des virgules).
     * - Autorise les méthodes HTTP courantes et tous les headers.
     * - Autorise l'utilisation des credentials (cookies / autorisation) si nécessaire.
     */

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Applique la configuration CORS à toutes les routes
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
