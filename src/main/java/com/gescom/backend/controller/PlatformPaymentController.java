package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.SaasPaymentRequest;
import com.gescom.backend.dto.platform.SaasPaymentResponse;
import com.gescom.backend.entity.SaasPayment;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.SaasPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * Registre des encaissements d'abonnement.
 *
 * A ne pas confondre avec {@code PaymentController}, qui concerne le terminal de caisse
 * d'une entreprise : ici, ce sont les versements des entreprises a GESCOM.
 */
@RestController
@RequestMapping("/api/platform/payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Paiements", description = "Encaissements des abonnements SaaS")
public class PlatformPaymentController {

    private final SaasPaymentService saasPaymentService;
    private final PlatformMapper platformMapper;

    public PlatformPaymentController(SaasPaymentService saasPaymentService, PlatformMapper platformMapper) {
        this.saasPaymentService = saasPaymentService;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des encaissements")
    public ResponseEntity<PageResponse<SaasPaymentResponse>> list(
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

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un encaissement")
    public ResponseEntity<SaasPaymentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(saasPaymentService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Enregistrer un encaissement",
               description = "Un succes renouvelle la periode de l'abonnement, un echec le passe en impaye")
    public ResponseEntity<SaasPaymentResponse> record(@Valid @RequestBody SaasPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(saasPaymentService.record(request)));
    }
}
