package com.gescom.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validateur personnalisé pour les emails
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {

    private static final String EMAIL_PATTERN =
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    private static final Pattern pattern = Pattern.compile(EMAIL_PATTERN);

    @Override
    public void initialize(ValidEmail constraintAnnotation) {
        // Initialisation si nécessaire
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.trim().isEmpty()) {
            return true; // Laisser @NotBlank gérer la validation
        }

        // Vérifications supplémentaires
        email = email.trim().toLowerCase();

        // Vérifier le pattern
        if (!pattern.matcher(email).matches()) {
            return false;
        }

        // Vérifications supplémentaires
        if (email.length() > 100) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("L'email ne peut pas dépasser 100 caractères")
                    .addConstraintViolation();
            return false;
        }

        // Vérifier les domaines interdits (exemple)
        String[] forbiddenDomains = {"tempmail.com", "10minutemail.com", "guerrillamail.com"};
        String domain = email.substring(email.indexOf("@") + 1);

        for (String forbiddenDomain : forbiddenDomains) {
            if (domain.equals(forbiddenDomain)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Ce domaine d'email n'est pas autorisé")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}