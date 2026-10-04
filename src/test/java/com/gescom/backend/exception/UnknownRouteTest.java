package com.gescom.backend.exception;

import com.gescom.backend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Une adresse inconnue est une erreur du client, pas une panne du serveur.
 *
 * {@code NoResourceFoundException} n'etait geree nulle part : elle arrivait dans le handler
 * generique, et l'API repondait 500 pour une simple erreur d'adresse. Par exemple, appeler
 * {@code /api/platform} au lieu de {@code /api/platform/dashboard} affichait « une erreur
 * interne est survenue » dans l'interface.
 *
 * Ce n'est pas qu'un detail : c'est ce qui permet de savoir si une erreur 500 vaut la peine
 * d'aller voir les logs. Si on melange les erreurs d'adresse avec les vraies erreurs
 * internes, on ne peut plus se fier ni aux unes ni aux autres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnknownRouteTest {

    @Mock private MessageSource messageSource;
    @Mock private HttpServletRequest request;

    @Test
    void uneAdresseInconnueRepond404EtNonPas500() {
        when(request.getRequestURI()).thenReturn("/api/platform");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler(messageSource)
                .handleNoResourceFound(
                        new NoResourceFoundException(HttpMethod.GET, "api/platform"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/platform");
        // Le message donne l'adresse fausse plutot que le detail interne qui a lance
        // l'exception : le gestionnaire de fichiers statiques n'apporte rien a l'appelant.
        assertThat(response.getBody().getMessage()).contains("/api/platform");
    }
}
