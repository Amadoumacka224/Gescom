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
 * Une adresse inconnue est une faute du client, pas une panne du serveur.
 *
 * {@code NoResourceFoundException} n'etait rattrapee par aucun cas particulier : elle tombait
 * dans le handler generique, et l'API repondait 500 a une simple faute d'adresse — au point
 * qu'un appel a {@code /api/platform} au lieu de {@code /api/platform/dashboard} faisait
 * afficher « une erreur interne est survenue » a l'interface.
 *
 * L'ecart n'est pas cosmetique : c'est lui qui decide si une alerte 500 merite qu'on ouvre les
 * journaux. Noyer les fautes d'adresse dans les erreurs internes revient a ne plus pouvoir se
 * fier a aucune des deux.
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
        // Le message nomme l'adresse fautive plutot que la mecanique interne qui a leve
        // l'exception : le gestionnaire de ressources statiques n'apprend rien a l'appelant.
        assertThat(response.getBody().getMessage()).contains("/api/platform");
    }
}
