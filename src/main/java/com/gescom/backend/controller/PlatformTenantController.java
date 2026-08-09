package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.CompanyProvisionRequest;
import com.gescom.backend.dto.platform.CompanyRequest;
import com.gescom.backend.dto.platform.CompanyResponse;
import com.gescom.backend.dto.platform.PlatformUserResponse;
import com.gescom.backend.dto.platform.SubscriptionResponse;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.CompanyService;
import com.gescom.backend.service.PlatformUserService;
import com.gescom.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Le parc : entreprises clientes et comptes utilisateurs, vus par le proprietaire de la plateforme.
 *
 * Les deux vont de pair — ouvrir un compte client cree l'entreprise et son administrateur, et
 * couper un acces se decide au niveau de l'un ou de l'autre. Ne double pas
 * {@code UserController}, qui sert l'ADMIN d'une entreprise sur ses propres comptes :
 * perimetres et droits distincts.
 *
 * Le prefixe {@code /api/platform} est deja reserve au SUPER_ADMIN par {@code SecurityConfig} ;
 * le {@code @PreAuthorize} de classe redouble la regle au niveau methode, de sorte qu'aucune
 * des deux barrieres ne soit seule a repondre de l'acces.
 *
 * Controleur mince, conformement au reste du projet : il delegue aux services et convertit
 * en DTO, sans jamais renvoyer d'entite ni traiter d'erreur — {@code GlobalExceptionHandler}
 * s'en charge.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformTenantController {

    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;
    private final PlatformUserService platformUserService;
    private final PlatformMapper platformMapper;

    public PlatformTenantController(CompanyService companyService,
                                    SubscriptionService subscriptionService,
                                    PlatformUserService platformUserService,
                                    PlatformMapper platformMapper) {
        this.companyService = companyService;
        this.subscriptionService = subscriptionService;
        this.platformUserService = platformUserService;
        this.platformMapper = platformMapper;
    }

    // ---------------------------------------------------------------- Entreprises

    @GetMapping("/companies")
    @Tag(name = "Plateforme - Entreprises", description = "Parc des entreprises clientes du SaaS")
    @Operation(summary = "Liste paginee des entreprises clientes")
    public ResponseEntity<PageResponse<CompanyResponse>> listCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<CompanyResponse> result = companyService
                .getCompanies(PageRequest.of(page, size))
                .map(platformMapper::toResponse);
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/companies/{id}")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Fiche d'une entreprise")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.getCompanyById(id)));
    }

    @GetMapping("/companies/{id}/subscriptions")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Historique des abonnements d'une entreprise")
    public ResponseEntity<List<SubscriptionResponse>> companySubscriptions(@PathVariable Long id) {
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
    @PostMapping("/companies")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Ouvrir un compte client",
               description = "Cree l'entreprise, son administrateur initial et son abonnement en une transaction")
    public ResponseEntity<CompanyResponse> provisionCompany(@Valid @RequestBody CompanyProvisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(companyService.provision(request)));
    }

    @PutMapping("/companies/{id}")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Modifier une entreprise")
    public ResponseEntity<CompanyResponse> updateCompany(@PathVariable Long id,
                                                         @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.updateCompany(id, request)));
    }

    @PatchMapping("/companies/{id}/suspend")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Suspendre l'acces d'une entreprise",
               description = "Coupe l'acces de tous ses utilisateurs sans supprimer la moindre donnee")
    public ResponseEntity<CompanyResponse> suspendCompany(@PathVariable Long id,
                                                          @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(companyService.suspend(id, reason)));
    }

    @PatchMapping("/companies/{id}/reactivate")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Retablir l'acces d'une entreprise")
    public ResponseEntity<CompanyResponse> reactivateCompany(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(companyService.reactivate(id)));
    }

    @PatchMapping("/companies/{id}/cancel")
    @Tag(name = "Plateforme - Entreprises")
    @Operation(summary = "Resilier un compte client",
               description = "Cloture l'abonnement et coupe l'acces ; les donnees sont conservees")
    public ResponseEntity<CompanyResponse> cancelCompany(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(platformMapper.toResponse(companyService.cancel(id, reason)));
    }

    // ---------------------------------------------------------------- Utilisateurs du parc

    @GetMapping("/users")
    @Tag(name = "Plateforme - Utilisateurs", description = "Comptes de toutes les entreprises clientes")
    @Operation(summary = "Liste paginee des utilisateurs du parc",
               description = "Filtrable par entreprise, role, statut et recherche libre")
    public ResponseEntity<PageResponse<PlatformUserResponse>> listUsers(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<PlatformUserResponse> result = platformUserService.search(
                companyId, role, active, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    /**
     * Active ou desactive un compte.
     *
     * Le levier operationnel du support : couper un acces compromis sans toucher au reste de
     * l'entreprise, la ou suspendre l'entreprise couperait tous ses utilisateurs.
     */
    @PatchMapping("/users/{id}/active")
    @Tag(name = "Plateforme - Utilisateurs")
    @Operation(summary = "Activer ou desactiver un compte")
    public ResponseEntity<PlatformUserResponse> setUserActive(@PathVariable Long id,
                                                              @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(platformUserService.setActive(id, active));
    }
}
