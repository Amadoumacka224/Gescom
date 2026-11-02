package com.gescom.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrôle du secret JWT au démarrage (JwtUtils.validateSecret). */
class JwtUtilsSecretTest {

    private JwtUtils withSecret(String secret) {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        return jwtUtils;
    }

    @Test
    @DisplayName("Un secret vide fait échouer le démarrage")
    void secretVide() {
        assertThatThrownBy(() -> withSecret("").validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un secret de moins de 32 octets fait échouer le démarrage")
    void secretTropCourt() {
        assertThatThrownBy(() -> withSecret("a".repeat(31)).validateSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un secret de 32 octets est accepté")
    void secretSuffisant() {
        assertThatCode(() -> withSecret("a".repeat(32)).validateSecret()).doesNotThrowAnyException();
    }
}
