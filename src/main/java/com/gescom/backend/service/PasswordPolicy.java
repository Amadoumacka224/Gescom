package com.gescom.backend.service;

import com.gescom.backend.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Regle de solidite des mots de passe, a un seul endroit.
 *
 * Sortie de {@code UserService} quand le back-office plateforme a eu besoin de la meme
 * verification : avec deux copies, on aurait fini par avoir des differences, et ca se
 * serait vu au pire moment (un mot de passe accepte a un endroit et refuse a l'autre).
 *
 * Les messages sont des cles de traduction, traduites par {@code GlobalExceptionHandler}
 * dans la langue de la requete.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * Verifie un mot de passe en clair et lance une {@link BusinessException} des la premiere
     * regle pas respectee.
     *
     * L'ordre compte : d'abord la longueur, l'erreur la plus courante, puis le contenu. Dire
     * « il manque une majuscule » pour un mot de passe de trois caracteres ne serait pas tres
     * utile.
     */
    public void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw BusinessException.of("user.password.empty", "Le mot de passe ne peut pas être vide");
        }
        if (password.length() < MIN_LENGTH) {
            throw BusinessException.of("user.password.tooShort",
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères",
                    MIN_LENGTH);
        }
        if (!password.matches(".*[A-Z].*")) {
            throw BusinessException.of("user.password.needsUppercase",
                    "Le mot de passe doit contenir au moins une lettre majuscule");
        }
        if (!password.matches(".*[a-z].*")) {
            throw BusinessException.of("user.password.needsLowercase",
                    "Le mot de passe doit contenir au moins une lettre minuscule");
        }
        if (!password.matches(".*\\d.*")) {
            throw BusinessException.of("user.password.needsDigit",
                    "Le mot de passe doit contenir au moins un chiffre");
        }
    }
}
