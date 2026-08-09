package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.SupportMessageRequest;
import com.gescom.backend.dto.platform.SupportTicketRequest;
import com.gescom.backend.dto.platform.SupportTicketResponse;
import com.gescom.backend.entity.User;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Billetterie du support.
 *
 * L'auteur des ecritures est pris du contexte de securite et jamais du corps de la requete :
 * un ticket ou un message doit porter l'identite reelle de celui qui l'a saisi.
 */
@RestController
@RequestMapping("/api/platform/support")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Support", description = "Tickets des entreprises clientes")
public class PlatformSupportController {

    private final SupportTicketService supportTicketService;
    private final PlatformMapper platformMapper;

    public PlatformSupportController(SupportTicketService supportTicketService,
                                     PlatformMapper platformMapper) {
        this.supportTicketService = supportTicketService;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Liste paginee des tickets")
    public ResponseEntity<PageResponse<SupportTicketResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<SupportTicketResponse> result = supportTicketService
                .search(status, priority, companyId, search,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(platformMapper::toSummary);

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/open-count")
    @Operation(summary = "Nombre de tickets encore a traiter")
    public ResponseEntity<Map<String, Long>> openCount() {
        return ResponseEntity.ok(Map.of("count", supportTicketService.countOpen()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un ticket, fil de discussion compris")
    public ResponseEntity<SupportTicketResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toDetail(supportTicketService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Ouvrir un ticket",
               description = "La description devient le premier message du fil")
    public ResponseEntity<SupportTicketResponse> open(@Valid @RequestBody SupportTicketRequest request,
                                                      @AuthenticationPrincipal User operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toDetail(supportTicketService.open(request, operator)));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Ajouter un message ou une note interne")
    public ResponseEntity<SupportTicketResponse> addMessage(@PathVariable Long id,
                                                            @Valid @RequestBody SupportMessageRequest request,
                                                            @AuthenticationPrincipal User author) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.addMessage(id, request, author)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un ticket")
    public ResponseEntity<SupportTicketResponse> changeStatus(@PathVariable Long id,
                                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.changeStatus(id, body.get("status"))));
    }

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Changer la priorite d'un ticket")
    public ResponseEntity<SupportTicketResponse> changePriority(@PathVariable Long id,
                                                                @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.changePriority(id, body.get("priority"))));
    }
}
