package com.gescom.backend.config;

import com.gescom.backend.security.CustomUserDetailsService;
import com.gescom.backend.security.JwtSecurityErrorHandler;
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
 * Configuration de la sécurité.
 * - Active Spring Security et les annotations de sécurité sur les méthodes.
 * - Déclare les beans pour l'authentification JWT, le CORS et l'encodage des mots de passe.
 */

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // Service qui charge les détails de l'utilisateur (username, password, roles).

    @Autowired
    private CustomUserDetailsService userDetailsService;

    // Réponses JSON quand la chaîne de filtres refuse : 401 (token absent ou invalide)
    // et 403 (connecté mais pas le bon rôle).
    @Autowired
    private JwtSecurityErrorHandler securityErrorHandler;

    // Origines autorisées pour le CORS, définies dans application.properties.
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Filtre d'authentification JWT.
     * Récupère et valide le token JWT de chaque requête.
     */

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {

        return new JwtAuthenticationFilter();
    }

    /**
     * Provider d'authentification DAO.
     * Utilise CustomUserDetailsService pour charger l'utilisateur
     * et le PasswordEncoder pour vérifier le mot de passe.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Expose l'AuthenticationManager pour pouvoir l'injecter ailleurs (ex : contrôleur d'authentification).
     * On le récupère depuis la configuration fournie par Spring.
     */

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }



    /**
     * Encodeur des mots de passe (BCrypt).
     *
     * Coût à 12 (le constructeur par défaut met 10), donc quatre fois plus de calcul par hash :
     * quelques centaines de millisecondes à la connexion, mais beaucoup plus long pour quelqu'un
     * qui voudrait casser des hash volés par force brute. Comme le coût est stocké dans le hash,
     * les anciens mots de passe en {@code $2a$10$} marchent toujours ; ils passeront à 12 la
     * prochaine fois qu'ils seront modifiés.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Configuration principale de la sécurité HTTP.
     * - Désactive CSRF (pas utile pour une API REST sans session).
     * - Gère les erreurs d'authentification avec un point d'entrée personnalisé.
     * - Pas de session côté serveur (STATELESS).
     * - Règles d'accès aux routes.
     * - Active le CORS.
     */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth.requestMatchers("/api/auth/**").permitAll()
                    // Health check de l'hebergeur, appele sans token depuis son reseau interne.
                    // Ca ne donne aucune info : show-details=never renvoie juste UP/DOWN, et
                    // aucun autre endpoint actuator n'est expose.
                    .requestMatchers("/actuator/health").permitAll()
                    // Documentation OpenAPI. Avant, ces routes tombaient dans le
                    // anyRequest().authenticated() de la fin : comme l'API n'a pas de session,
                    // le navigateur ne peut pas envoyer de token, et l'interface citée dans le
                    // README répondait 401, donc inutilisable.
                    //
                    // Les ouvrir ne montre que la structure de l'API, aucune donnée : les tests
                    // lancés depuis l'interface passent par les mêmes règles que les autres
                    // appels et échouent sans token valide. Par sécurité, le profil `render`
                    // désactive quand même springdoc, pas besoin de documenter publiquement
                    // une installation en production (voir application-render.properties).
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    // Espace du super admin. Chaque contrôleur a aussi son @PreAuthorize :
                    // la règle sur l'URL protège tout le préfixe, l'annotation protège la
                    // méthode. Comme ça, un contrôleur ajouté sous ce préfixe est protégé
                    // même si on oublie l'annotation.
                    .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")
                    // Et inversement, le super admin n'a rien à faire dans les écrans métier :
                    // il n'appartient à aucune entreprise, et avec un contexte d'entreprise
                    // vide il verrait les données de toutes les entreprises.
                    .requestMatchers("/api/**").hasAnyRole("ADMIN", "CAISSIER")
                    .anyRequest().authenticated()
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        // Ajoute le provider DAO (UserDetailsService + PasswordEncoder)
        http.authenticationProvider(authenticationProvider());
        // Ajoute le filtre JWT avant le filtre d'authentification standard
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuration CORS.
     * - Origines autorisées lues dans `cors.allowed-origins` (séparées par des virgules).
     * - Autorise les méthodes HTTP courantes et tous les headers.
     * - Autorise les credentials (cookies / Authorization) si besoin.
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
