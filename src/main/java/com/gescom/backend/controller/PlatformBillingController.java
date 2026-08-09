package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlanRequest;
import com.gescom.backend.dto.platform.PlanResponse;
import com.gescom.backend.dto.platform.SaasPaymentRequest;
import com.gescom.backend.dto.platform.SaasPaymentResponse;
import com.gescom.backend.dto.platform.SubscriptionRequest;
import com.gescom.backend.dto.platform.SubscriptionResponse;
import com.gescom.backend.entity.SaasPayment;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.PlanService;
import com.gescom.backend.service.SaasPaymentService;
import com.gescom.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La chaine d'abonnement : catalogue des formules, contrats souscrits et encaissements.
 *
 * Les trois se suivent — on souscrit une formule, le contrat court, les versements le
 * renouvellent — et se lisent ensemble. Modifier un tarif au catalogue ne touche pas aux
 * contrats en cours ({@code Subscription.amount} est fige a la souscription) : la revision
 * ne vaut que pour les souscriptions a venir.
 *
 * A ne pas confondre avec {@code PaymentController}, qui concerne le terminal de caisse
 * d'une entreprise : ici, ce sont les versements des entreprises a GESCOM.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformBillingController {

    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final SaasPaymentService saasPaymentService;
    private final PlatformMapper platformMapper;

    public PlatformBillingController(PlanService planService,
                                     SubscriptionService subscriptionService,
                                     SaasPaymentService saasPaymentService,
                                     PlatformMapper platformMapper) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.saasPaymentService = saasPaymentService;
        this.platformMapper = platformMapper;
    }

    // ---------------------------------------------------------------- Catalogue des formules

    @GetMapping("/plans")
    @Tag(name = "Plateforme - Formules", description = "Catalogue des formules d'abonnement")
    @Operation(summary = "Catalogue complet", description = "Formules actives et retirees")
    public ResponseEntity<List<PlanResponse>> listPlans() {
        return ResponseEntity.ok(planService.getAll().stream()
                .map(platformMapper::toResponse)
                .toList());
    }

    @GetMapping("/plans/{id}")
    @Tag(name = "Plateforme - Formules")
    @Operation(summary = "Detail d'une formule")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(planService.getById(id)));
    }

    @PostMapping("/plans")
    @Tag(name = "Plateforme - Formules")
    @Operation(summary = "Creer une formule")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(planService.create(request)));
    }

    @PutMapping("/plans/{id}")
    @Tag(name = "Plateforme - Formules")
    @Operation(summary = "Modifier une formule",
               description = "Le code n'est pas modifiable ; les contrats en cours gardent leur tarif")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable Long id,
                                                   @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(platformMapper.toResponse(planService.update(id, request)));
    }

    @PatchMapping("/plans/{id}/active")
    @Tag(name = "Plateforme - Formules")
    @Operation(summary = "Retirer ou remettre une formule au catalogue",
               description = "Sans effet sur les contrats en cours, qui continuent de courir")
    public ResponseEntity<PlanResponse> setPlanActive(@PathVariable Long id,
                                                      @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(platformMapper.toResponse(planService.setActive(id, active)));
    }

    @DeleteMapping("/plans/{id}")
    @Tag(name = "Plateforme - Formules")
    @Operation(summary = "Supprimer une formule",
               description = "Refuse des qu'un abonnement, meme resilie, s'y rattache")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Contrats

    @GetMapping("/subscriptions")
    @Tag(name = "Plateforme - Abonnements", description = "Contrats d'abonnement des entreprises clientes")
    @Operation(summary = "Liste paginee des abonnements")
    public ResponseEntity<PageResponse<SubscriptionResponse>> listSubscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Subscription.SubscriptionStatus parsed = status == null || status.isBlank()
                ? null
                : Subscription.SubscriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        Page<SubscriptionResponse> result = subscriptionService
                .getSubscriptions(parsed, PageRequest.of(page, size))
                .map(platformMapper::toResponse);
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/subscriptions")
    @Tag(name = "Plateforme - Abonnements")
    @Operation(summary = "Souscrire ou changer de formule",
               description = "Cloture le contrat en cours le cas echeant : une entreprise n'a qu'un contrat vivant")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(subscriptionService.subscribe(request)));
    }

    @PatchMapping("/subscriptions/{id}/renew")
    @Tag(name = "Plateforme - Abonnements")
    @Operation(summary = "Renouveler pour une periode")
    public ResponseEntity<SubscriptionResponse> renewSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(subscriptionService.renew(id)));
    }

    @PatchMapping("/subscriptions/{id}/cancel")
    @Tag(name = "Plateforme - Abonnements")
    @Operation(summary = "Resilier un abonnement")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(@PathVariable Long id,
                                                                   @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(subscriptionService.cancel(id, reason)));
    }

    // ---------------------------------------------------------------- Encaissements

    @GetMapping("/payments")
    @Tag(name = "Plateforme - Paiements", description = "Encaissements des abonnements SaaS")
    @Operation(summary = "Liste paginee des encaissements")
    public ResponseEntity<PageResponse<SaasPaymentResponse>> listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        SaasPayment.SaasPaymentStatus parsed = status == null || status.isBlank()
                ? null
                : SaasPayment.SaasPaymentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        Page<SaasPaymentResponse> result = saasPaymentService
                .getPayments(parsed, companyId, PageRequest.of(page, size))
                .map(platformMapper::toResponse);
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/payments/{id}")
    @Tag(name = "Plateforme - Paiements")
    @Operation(summary = "Detail d'un encaissement")
    public ResponseEntity<SaasPaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(saasPaymentService.getById(id)));
    }

    @PostMapping("/payments")
    @Tag(name = "Plateforme - Paiements")
    @Operation(summary = "Enregistrer un encaissement",
               description = "Un succes renouvelle la periode de l'abonnement, un echec le passe en impaye")
    public ResponseEntity<SaasPaymentResponse> recordPayment(@Valid @RequestBody SaasPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(saasPaymentService.record(request)));
    }
}
