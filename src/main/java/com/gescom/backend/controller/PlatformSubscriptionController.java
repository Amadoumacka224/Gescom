package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlanResponse;
import com.gescom.backend.dto.platform.SubscriptionRequest;
import com.gescom.backend.dto.platform.SubscriptionResponse;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.repository.PlanRepository;
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
 * Abonnements et catalogue des formules.
 *
 * Les formules sont exposees ici plutot que dans un controleur separe : elles ne servent
 * qu'a souscrire, et l'ecran d'abonnements est le seul a les consulter.
 */
@RestController
@RequestMapping("/api/platform/subscriptions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Abonnements", description = "Contrats et catalogue des formules")
public class PlatformSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;
    private final PlatformMapper platformMapper;

    public PlatformSubscriptionController(SubscriptionService subscriptionService,
                                          PlanRepository planRepository,
                                          PlatformMapper platformMapper) {
        this.subscriptionService = subscriptionService;
        this.planRepository = planRepository;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des abonnements")
    public ResponseEntity<PageResponse<SubscriptionResponse>> list(
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

    @GetMapping("/plans")
    @Operation(summary = "Catalogue des formules")
    public ResponseEntity<List<PlanResponse>> plans() {
        return ResponseEntity.ok(planRepository.findAllByOrderBySortOrderAsc().stream()
                .map(platformMapper::toResponse)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Souscrire ou changer de formule",
               description = "Cloture le contrat en cours le cas echeant : une entreprise n'a qu'un contrat vivant")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(subscriptionService.subscribe(request)));
    }

    @PatchMapping("/{id}/renew")
    @Operation(summary = "Renouveler pour une periode")
    public ResponseEntity<SubscriptionResponse> renew(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(subscriptionService.renew(id)));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Resilier un abonnement")
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(subscriptionService.cancel(id, reason)));
    }
}
