package com.gescom.validation;

import com.gescom.dto.UserRegistrationDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validateur pour vérifier que les mots de passe correspondent
 */
public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, UserRegistrationDto> {

    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        // Initialisation si nécessaire
    }

    @Override
    public boolean isValid(UserRegistrationDto userDto, ConstraintValidatorContext context) {
        if (userDto == null) {
            return true; // Laisser @NotNull gérer la validation null
        }

        String password = userDto.getPassword();
        String confirmPassword = userDto.getConfirmPassword();

        // Si l'un des deux est null, laisser @NotBlank gérer
        if (password == null || confirmPassword == null) {
            return true;
        }

        boolean isValid = password.equals(confirmPassword);

        if (!isValid) {
            // Désactiver le message par défaut
            context.disableDefaultConstraintViolation();

            // Ajouter le message d'erreur au champ confirmPassword
            context.buildConstraintViolationWithTemplate("Les mots de passe ne correspondent pas")
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }

        return isValid;
    }
}