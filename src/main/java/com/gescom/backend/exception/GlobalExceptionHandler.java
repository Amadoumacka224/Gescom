package com.gescom.backend.exception;

import com.gescom.backend.dto.common.ErrorResponse;
import com.gescom.backend.tenancy.TenantViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire d'exceptions centralisé pour toute l'API (@RestControllerAdvice).
 * Convertit chaque type d'exception en une réponse HTTP cohérente ({@link ErrorResponse}),
 * ce qui évite de dupliquer la gestion d'erreurs dans chaque contrôleur :
 *   - métier / validation / argument invalide → 400, authentification → 401, accès refusé → 403,
 *     ressource absente → 404, méthode non supportée → 405, doublon / conflit BDD → 409,
 *     et tout le reste → 500.
 * Le handler générique (Exception) journalise la trace complète avec un {@code traceId}
 * mais n'expose jamais le détail technique au client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Préfixes des arguments qui sont eux-mêmes des clés à traduire. Un message comme
     * « {0} non trouvé(e) avec l'id : {1} » reçoit le nom de l'entité en argument : sans cette
     * résolution en deux temps, il s'afficherait « resource.order non trouvé(e) ».
     */
    private static final String[] NESTED_KEY_PREFIXES = { "resource.", "field." };

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Message destiné à l'utilisateur, dans la langue de la requête courante.
     *
     * La locale vient de l'en-tête `Accept-Language` (cf. `LocaleConfiguration`). Une clé absente
     * des catalogues retombe sur le littéral français porté par l'exception plutôt que de faire
     * échouer la réponse : un catalogue incomplet dégrade le message, il ne casse pas l'API.
     */
    private String localize(Throwable ex) {
        if (!(ex instanceof LocalizedException localized) || localized.getMessageKey() == null) {
            return ex.getMessage();
        }
        Locale locale = LocaleContextHolder.getLocale();
        Object[] args = Arrays.stream(localized.getMessageArgs())
                .map(arg -> resolveNested(arg, locale))
                .toArray();
        return messageSource.getMessage(localized.getMessageKey(), args, ex.getMessage(), locale);
    }

    /** Traduit un argument qui est lui-même une clé (`resource.order`, `field.code`). */
    private Object resolveNested(Object arg, Locale locale) {
        if (!(arg instanceof String text)) return arg;
        for (String prefix : NESTED_KEY_PREFIXES) {
            if (text.startsWith(prefix)) {
                return messageSource.getMessage(text, null, text.substring(prefix.length()), locale);
            }
        }
        return arg;
    }

    // --- 400 Bad Request -----------------------------------------------------

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, localize(ex), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, localize(ex), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Requête mal formée ou valeur invalide", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Valeur invalide pour le paramètre '" + ex.getName() + "' : " + ex.getValue(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre obligatoire manquant : '" + ex.getParameterName() + "'", request);
    }

    // Échec de validation des @Valid sur le corps : renvoie le détail champ par champ pour que le
    // frontend puisse afficher le message sous le bon formulaire.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse body = ErrorResponse.validation(
                status.value(), status.getReasonPhrase(),
                "Un ou plusieurs champs sont invalides", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    // Échec de validation sur les paramètres (@Validated sur @RequestParam / @PathVariable).
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // --- 401 Unauthorized ----------------------------------------------------

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Ce compte est désactivé. Contactez un administrateur.", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Nom d'utilisateur ou mot de passe incorrect.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Échec de l'authentification.", request);
    }

    // --- 403 Forbidden -------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour cette action.", request);
    }

    /**
     * Tentative d'écriture sur une donnée appartenant à une autre entreprise.
     *
     * Journalisé en {@code warn} et non silencieusement : en lecture, une ressource d'autrui
     * se comporte comme une ressource inexistante, mais une écriture hors périmètre signale
     * soit un défaut de code, soit une tentative délibérée — les deux méritent une trace.
     */
    @ExceptionHandler(TenantViolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantViolation(TenantViolationException ex, HttpServletRequest request) {
        log.warn("Cloisonnement enfreint sur {} : {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, localize(ex), request);
    }

    // --- 404 Not Found -------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, localize(ex), request);
    }

    /**
     * URL qui ne correspond à aucun point d'entrée.
     *
     * Sans ce cas, l'exception tombait dans le handler générique et l'API répondait **500**
     * sur une simple faute d'adresse — `/api/platform` au lieu de `/api/platform/dashboard`,
     * par exemple. Une adresse inconnue est une erreur du client, pas une panne du serveur :
     * la distinction est ce qui permet de ne s'alarmer que des vraies 500, et elle évite au
     * frontend d'afficher « une erreur interne est survenue » quand il s'est trompé de route.
     *
     * Spring lève ici {@code NoResourceFoundException} parce que la requête, faute de
     * contrôleur, finit chez le gestionnaire de ressources statiques ; le message renvoyé ne
     * le mentionne pas, cette mécanique interne n'apprenant rien à l'appelant.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND,
                "Aucune ressource ne correspond à '" + request.getRequestURI() + "'", request);
    }

    // --- 405 Method Not Allowed ----------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Méthode HTTP '" + ex.getMethod() + "' non supportée pour cette ressource", request);
    }

    // --- 409 Conflict --------------------------------------------------------

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, localize(ex), request);
    }

    // Contrainte d'intégrité violée au niveau de la base (unicité, clé étrangère...).
    // Le détail technique est journalisé mais on renvoie un message générique au client.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violation d'intégrité des données sur {} : {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "Opération impossible : elle viole une contrainte d'intégrité des données.", request);
    }

    // --- 500 Internal Server Error -------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Erreur inattendue [traceId={}] sur {} {}", traceId, request.getMethod(), request.getRequestURI(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse body = ErrorResponse.internal(
                status.value(), status.getReasonPhrase(),
                "Une erreur interne est survenue. Communiquez la référence ci-dessous au support.",
                request.getRequestURI(), traceId);
        return ResponseEntity.status(status).body(body);
    }

    /** Construit la réponse standard à partir d'un statut et d'un message. */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
