package com.gescom.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description OpenAPI de l'API et déclaration de l'authentification.
 *
 * Sans le {@link SecurityScheme} ci-dessous, Swagger s'affiche mais ne sert pas à grand-chose :
 * on ne peut pas ajouter de token aux requêtes de test, donc tout ce qui n'est pas
 * /api/auth/login répond 401. C'est le bouton « Authorize » qui permet de vraiment tester
 * l'API depuis l'interface.
 *
 * Le schéma est aussi déclaré pour toutes les routes, comme ça pas besoin d'annoter chaque
 * méthode. Les quelques routes publiques (/api/auth/**, /actuator/health) ne sont pas
 * gênées par un en-tête Authorization en trop, alors qu'un token oublié là où il faut
 * donnerait un 401 à chaque essai.
 *
 * Utilisation : POST /api/auth/login, copier le {@code token} de la réponse et le coller
 * dans « Authorize ». Ne pas taper « Bearer », Swagger l'ajoute tout seul.
 */
@Configuration
public class OpenApiConfig {

    /** Nom du schéma, repris tel quel dans le document OpenAPI généré. */
    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI gescomOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                // Juste pour la doc, ça ne change pas la validation : c'est ce qui affiche
                // « JWT » dans l'interface.
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Jeton renvoyé par POST /api/auth/login, sans le préfixe « Bearer ».");

        return new OpenAPI()
                .info(new Info()
                        .title("GESCOM - API de gestion commerciale")
                        .version("1.0.0")
                        .description("""
                                API REST du back-office GESCOM : ventes, stocks, facturation, \
                                livraisons et retours clients, ainsi que le plan de contrôle SaaS \
                                sous /api/platform.

                                Authentification par jeton JWT. Trois rôles : ADMIN et CAISSIER \
                                pour les écrans métier d'une entreprise, SUPER_ADMIN pour le \
                                back-office de la plateforme - les périmètres sont disjoints, un \
                                SUPER_ADMIN n'accède à aucune route métier.

                                Les données sont cloisonnées par entreprise : une requête ne voit \
                                jamais que les lignes de l'entreprise de son porteur de jeton.""")
                        .contact(new Contact().name("GESCOM"))
                        .license(new License().name("Usage interne")))
                .components(new Components().addSecuritySchemes(SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
