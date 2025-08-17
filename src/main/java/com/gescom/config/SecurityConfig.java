package com.gescom.config;

// src/main/java/com/company/commercial/config/SecurityConfig.java
import com.gescom.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        authProvider.setHideUserNotFoundExceptions(false);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setUseReferer(false);
        handler.setDefaultTargetUrl("/dashboard");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Désactiver CSRF pour H2 Console (development seulement)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**"))
                )

                // Configuration des autorisations (CORRIGÉE)
                .authorizeHttpRequests(authz -> authz
                        // Ressources publiques - ORDRE IMPORTANT !

                        .requestMatchers(
                                "/",
                                "/login**",
                                "/register**",
                                "/forgot-password**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/h2-console/**",
                                "/error**"
                        ).permitAll()

                        // Pages d'administration
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/manager/**").hasAnyRole("ADMIN", "MANAGER")

                        // Pages métier
                        .requestMatchers("/dashboard").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/profile/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/clients/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/products/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/orders/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/invoices/**").hasAnyRole("ADMIN", "MANAGER", "USER")
                        .requestMatchers("/reports/**").hasAnyRole("ADMIN", "MANAGER")

                        // Tout le reste nécessite une authentification
                        .anyRequest().authenticated()
                )

                // Configuration du login (SIMPLIFIÉE)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler())
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // Configuration du logout
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout=true")
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )

                // Gestion des sessions
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?expired=true")
                )

                // Configuration des headers pour H2 Console
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                )

                // Gestion des exceptions
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            try {
                                response.sendRedirect("/error");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                );

        return http.build();
    }
}
