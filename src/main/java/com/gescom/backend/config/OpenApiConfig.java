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
 * Description OpenAPI de l'API et déclaration du schéma d'authentification.
 *
 * Sans le {@link SecurityScheme} ci-dessous, l'interface Swagger s'affiche mais ne sert à
 * rien : elle n'a aucun moyen de joindre un jeton aux requêtes d'essai, si bien que tout
 * appel autre que /api/auth/login répond 401. C'est ce bouton « Authorize » qui fait la
 * différence entre une documentation à lire et une API que l'on peut dérouler en direct.
 *
 * Le schéma est également posé en exigence globale : toutes les routes en héritent, ce qui
 * évite d'annoter chaque méthode. Les rares points ouverts (/api/auth/**, /actuator/health)
 * s'accommodent d'un en-tête d'autorisation superflu — l'inverse, oublier le jeton là où il
 * est requis, coûterait un 401 à chaque essai.
 *
 * Mode d'emploi : POST /api/auth/login, copier la valeur du champ {@code token} de la
 * réponse, la coller dans « Authorize ». Le préfixe « Bearer » est ajouté par l'interface,
 * il ne doit pas être saisi.
 */
@Configuration
public class OpenApiConfig {

    /** Nom interne du schéma, repris tel quel dans le document OpenAPI produit. */
    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI gescomOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                // Renseigne le format sans changer la validation : purement documentaire,
                // mais c'est ce qui fait afficher « JWT » dans l'interface.
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Jeton renvoyé par POST /api/auth/login, sans le préfixe « Bearer ».");

        return new OpenAPI()
                .info(new Info()
                        .title("GESCOM — API de gestion commerciale")
                        .version("1.0.0")
                        .description("""
                                API REST du back-office GESCOM : ventes, stocks, facturation, \
                                livraisons et retours clients, ainsi que le plan de contrôle SaaS \
                                sous /api/platform.

                                Authentification par jeton JWT. Trois rôles : ADMIN et CAISSIER \
                                pour les écrans métier d'une entreprise, SUPER_ADMIN pour le \
                                back-office de la plateforme — les périmètres sont disjoints, un \
                                SUPER_ADMIN n'accède à aucune route métier.

                                Les données sont cloisonnées par entreprise : une requête ne voit \
                                jamais que les lignes de l'entreprise de son porteur de jeton.""")
                        .contact(new Contact().name("GESCOM"))
                        .license(new License().name("Usage interne")))
                .components(new Components().addSecuritySchemes(SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
