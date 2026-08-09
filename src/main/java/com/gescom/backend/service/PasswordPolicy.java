package com.gescom.backend.service;

import com.gescom.backend.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Regle unique de solidite des mots de passe.
 *
 * Extraite de {@code UserService} le jour ou le back-office proprietaire a eu besoin de la
 * meme verification : deux copies de la meme politique auraient fini par diverger, et la
 * divergence se serait vue au pire endroit — un mot de passe accepte ici, refuse ailleurs.
 *
 * Les messages sont portes par des cles de traduction, resolues par
 * {@code GlobalExceptionHandler} avec la locale de la requete.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * Verifie un mot de passe en clair et leve une {@link BusinessException} au premier
     * manquement.
     *
     * L'ordre des controles n'est pas neutre : la longueur d'abord, qui est le reproche le
     * plus frequent, puis la composition. Signaler « il manque une majuscule » sur un mot de
     * passe de trois caracteres serait une aide de mauvaise qualite.
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
