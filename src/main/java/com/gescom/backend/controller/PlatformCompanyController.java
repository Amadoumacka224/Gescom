package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.CompanyProvisionRequest;
import com.gescom.backend.dto.platform.CompanyRequest;
import com.gescom.backend.dto.platform.CompanyResponse;
import com.gescom.backend.dto.platform.SubscriptionResponse;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.CompanyService;
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
import java.util.Map;

/**
 * Gestion du parc d'entreprises clientes.
 *
 * Controleur mince, conformement au reste du projet : il delegue aux services et convertit
 * en DTO, sans jamais renvoyer d'entite ni traiter d'erreur — {@code GlobalExceptionHandler}
 * s'en charge.
 */
@RestController
@RequestMapping("/api/platform/companies")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Entreprises", description = "Parc des entreprises clientes du SaaS")
public class PlatformCompanyController {

    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;
    private final PlatformMapper platformMapper;

    public PlatformCompanyController(CompanyService companyService,
                                     SubscriptionService subscriptionService,
                                     PlatformMapper platformMapper) {
        this.companyService = companyService;
        this.subscriptionService = subscriptionService;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des entreprises clientes")
    public ResponseEntity<PageResponse<CompanyResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<CompanyResponse> result = companyService
                .getCompanies(PageRequest.of(page, size))
                .map(platformMapper::toResponse);
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'une entreprise")
    public ResponseEntity<CompanyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.getCompanyById(id)));
    }

    @GetMapping("/{id}/subscriptions")
    @Operation(summary = "Historique des abonnements d'une entreprise")
    public ResponseEntity<List<SubscriptionResponse>> subscriptions(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getHistoryForCompany(id).stream()
                .map(platformMapper::toResponse)
                .toList());
    }

    /**
     * Ouvre un compte client complet : entreprise, administrateur initial et abonnement.
     *
     * Une seule transaction cote service — une entreprise creee sans administrateur serait
     * un compte inaccessible.
     */
    @PostMapping
    @Operation(summary = "Ouvrir un compte client",
               description = "Cree l'entreprise, son administrateur initial et son abonnement en une transaction")
    public ResponseEntity<CompanyResponse> provision(@Valid @RequestBody CompanyProvisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(companyService.provision(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une entreprise")
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.updateCompany(id, request)));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspendre l'acces d'une entreprise",
               description = "Coupe l'acces de tous ses utilisateurs sans supprimer la moindre donnee")
    public ResponseEntity<CompanyResponse> suspend(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(companyService.suspend(id, reason)));
    }

    @PatchMapping("/{id}/reactivate")
    @Operation(summary = "Retablir l'acces d'une entreprise")
    public ResponseEntity<CompanyResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.reactivate(id)));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Resilier un compte client",
               description = "Cloture l'abonnement et coupe l'acces ; les donnees sont conservees")
    public ResponseEntity<CompanyResponse> cancel(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(companyService.cancel(id, reason)));
    }
}
