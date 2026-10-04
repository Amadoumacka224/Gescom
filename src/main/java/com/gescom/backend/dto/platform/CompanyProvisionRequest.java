package com.gescom.backend.dto.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creation d'un compte client : l'entreprise, son premier admin et son abonnement en une
 * seule fois.
 *
 * Les trois vont ensemble, c'est pour ca qu'ils sont dans la meme requete. Une entreprise
 * sans admin serait inaccessible, et il faudrait une deuxieme requete pour la rendre
 * utilisable, qui pourrait echouer et laisser un compte a moitie cree en base. Le service
 * fait tout dans une seule transaction.
 */
public record CompanyProvisionRequest(
        @NotNull(message = "Les informations de l'entreprise sont obligatoires")
        @Valid
        CompanyRequest company,

        @NotNull(message = "L'administrateur initial est obligatoire")
        @Valid
        InitialAdmin admin,

        /** Formule choisie ; si vide, l'entreprise demarre en essai sans abonnement. */
        Long planId,

        /** MONTHLY ou YEARLY ; ignore si aucune formule n'est choisie. */
        String billingPeriod,

        /**
         * Vrai pour demarrer en periode d'essai : l'abonnement est en TRIALING et la date de
         * fin est calculee avec le nombre de jours d'essai de la formule.
         */
        Boolean startTrial
) {

    /**
     * Premier admin de l'entreprise.
     *
     * Le mot de passe arrive en clair (en HTTPS) puis est encode en BCrypt des la reception ;
     * il n'est jamais logge ni renvoye.
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
