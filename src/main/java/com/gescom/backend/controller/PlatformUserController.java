package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlatformUserResponse;
import com.gescom.backend.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Utilisateurs de tout le parc, vus par le proprietaire de la plateforme.
 *
 * Ne double pas {@code UserController} : celui-ci sert l'ADMIN d'une entreprise sur ses
 * propres comptes, celui-la supervise l'ensemble. Perimetres et droits distincts.
 */
@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Utilisateurs", description = "Comptes de toutes les entreprises clientes")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des utilisateurs du parc",
               description = "Filtrable par entreprise, role, statut et recherche libre")
    public ResponseEntity<PageResponse<PlatformUserResponse>> list(
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
    @PatchMapping("/{id}/active")
    @Operation(summary = "Activer ou desactiver un compte")
    public ResponseEntity<PlatformUserResponse> setActive(@PathVariable Long id,
                                                          @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(platformUserService.setActive(id, active));
    }
}
