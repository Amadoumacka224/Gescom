package com.gescom.backend.dto.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ouverture d'un compte client : l'entreprise, son premier administrateur et son abonnement
 * en une seule operation.
 *
 * Les trois sont indissociables et c'est pourquoi ils voyagent ensemble. Une entreprise sans
 * administrateur serait inaccessible a son proprietaire, et il faudrait alors une seconde
 * requete pour la rendre utilisable — avec le risque qu'elle echoue et laisse un compte
 * orphelin en base. La creation est traitee en une transaction unique cote service.
 */
public record CompanyProvisionRequest(
        @NotNull(message = "Les informations de l'entreprise sont obligatoires")
        @Valid
        CompanyRequest company,

        @NotNull(message = "L'administrateur initial est obligatoire")
        @Valid
        InitialAdmin admin,

        /** Formule souscrite ; laissee vide, l'entreprise demarre en essai sans abonnement. */
        Long planId,

        /** MONTHLY ou YEARLY ; ignore si aucune formule n'est choisie. */
        String billingPeriod,

        /**
         * Vrai pour ouvrir le compte en periode d'essai : l'abonnement demarre en TRIALING et
         * l'echeance est calculee a partir du nombre de jours d'essai de la formule.
         */
        Boolean startTrial
) {

    /**
     * Administrateur initial de l'entreprise.
     *
     * Le mot de passe est transmis en clair sur le canal HTTPS puis encode au BCrypt du
     * projet des la reception ; il n'est ni journalise ni renvoye.
     */
    public record InitialAdmin(
            @NotBlank(message = "Le nom d'utilisateur est obligatoire")
            @Size(min = 3, max = 50)
            String username,

            @NotBlank(message = "L'email est obligatoire")
            @Email(message = "Format d'email invalide")
            @Size(max = 100)
            String email,

            @NotBlank(message = "Le mot de passe est obligatoire")
            @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caracteres")
            String password,

            @NotBlank(message = "Le prenom est obligatoire")
            @Size(max = 100)
            String firstName,

            @NotBlank(message = "Le nom est obligatoire")
            @Size(max = 100)
            String lastName
    ) {
    }
}
