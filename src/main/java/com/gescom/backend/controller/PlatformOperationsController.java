package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlatformAccountRequest;
import com.gescom.backend.dto.platform.PlatformActivityResponse;
import com.gescom.backend.dto.platform.PlatformDashboardResponse;
import com.gescom.backend.dto.platform.PlatformNotificationResponse;
import com.gescom.backend.dto.platform.PlatformSettingsRequest;
import com.gescom.backend.dto.platform.PlatformSettingsResponse;
import com.gescom.backend.dto.platform.SupportMessageRequest;
import com.gescom.backend.dto.platform.SupportTicketRequest;
import com.gescom.backend.dto.platform.SupportTicketResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.PlatformSettings;
import com.gescom.backend.entity.User;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.PlatformMetricsService;
import com.gescom.backend.service.PlatformNotificationService;
import com.gescom.backend.service.PlatformSettingsService;
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

import java.util.Locale;
import java.util.Map;

/**
 * L'exploitation courante de la plateforme : tableau de bord, journal, notifications,
 * reglages et support.
 *
 * Ce sont les surfaces de surveillance du proprietaire, la ou {@code PlatformTenantController}
 * gere le parc et {@code PlatformBillingController} la chaine d'abonnement. Le tableau de bord
 * decrit l'etat courant, les notifications ce qui s'est passe, le journal qui l'a fait.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformOperationsController {

    private final PlatformMetricsService platformMetricsService;
    private final ActivityLogService activityLogService;
    private final PlatformNotificationService notificationService;
    private final PlatformSettingsService platformSettingsService;
    private final SupportTicketService supportTicketService;
    private final PlatformMapper platformMapper;

    public PlatformOperationsController(PlatformMetricsService platformMetricsService,
                                        ActivityLogService activityLogService,
                                        PlatformNotificationService notificationService,
                                        PlatformSettingsService platformSettingsService,
                                        SupportTicketService supportTicketService,
                                        PlatformMapper platformMapper) {
        this.platformMetricsService = platformMetricsService;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
        this.platformSettingsService = platformSettingsService;
        this.supportTicketService = supportTicketService;
        this.platformMapper = platformMapper;
    }

    // ---------------------------------------------------------------- Tableau de bord

    @GetMapping("/dashboard")
    @Tag(name = "Plateforme - Tableau de bord", description = "Indicateurs consolides du parc SaaS")
    @Operation(summary = "Indicateurs consolides",
               description = "Entreprises, abonnements, MRR/ARR, paiements, churn, alertes et sante de la plateforme")
    public ResponseEntity<PlatformDashboardResponse> getDashboard() {
        return ResponseEntity.ok(platformMetricsService.buildDashboard());
    }

    // ---------------------------------------------------------------- Journal d'activite

    /**
     * Reutilise {@code ActivityLogService.searchActivities} tel quel : le filtre de cloisonnement
     * etant inactif pour le SUPER_ADMIN, la meme requete qui ne montre a une entreprise que ses
     * propres traces retourne ici celles de tout le parc. C'est le benefice direct d'un
     * cloisonnement porte par l'infrastructure plutot que recopie dans chaque service.
     */
    @GetMapping("/activity")
    @Tag(name = "Plateforme - Activite", description = "Journal consolide de toutes les entreprises")
    @Operation(summary = "Journal d'activite de toutes les entreprises")
    public ResponseEntity<PageResponse<PlatformActivityResponse>> listActivity(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        ActivityLog.ActionType parsed = actionType == null || actionType.isBlank()
                ? null
                : ActivityLog.ActionType.valueOf(actionType.trim().toUpperCase(Locale.ROOT));

        Page<PlatformActivityResponse> result = activityLogService
                .searchActivities(null, parsed, entity, null, null, search,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toActivityResponse);

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    private PlatformActivityResponse toActivityResponse(ActivityLog log) {
        User user = log.getUser();
        Company company = log.getOwnerCompany();
        return new PlatformActivityResponse(
                log.getId(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                user != null ? user.getFirstName() + " " + user.getLastName() : null,
                user != null ? user.getRole().name() : null,
                log.getActionType().name(),
                log.getEntity(),
                log.getEntityId(),
                log.getDescription(),
                log.getCreatedAt()
        );
    }

    // ---------------------------------------------------------------- Notifications

    @GetMapping("/notifications")
    @Tag(name = "Plateforme - Notifications", description = "Evenements notables de la plateforme")
    @Operation(summary = "Liste paginee des notifications")
    public ResponseEntity<PageResponse<PlatformNotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<PlatformNotificationResponse> result = notificationService
                .list(unreadOnly, PageRequest.of(page, size))
                .map(platformMapper::toResponse);

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    /** Compteur du badge : appele a chaque affichage de l'interface, servi par un index partiel. */
    @GetMapping("/notifications/unread-count")
    @Tag(name = "Plateforme - Notifications")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<Map<String, Long>> unreadNotificationCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread()));
    }

    @PatchMapping("/notifications/{id}/read")
    @Tag(name = "Plateforme - Notifications")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<PlatformNotificationResponse> markNotificationRead(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(notificationService.markRead(id)));
    }

    @PatchMapping("/notifications/read-all")
    @Tag(name = "Plateforme - Notifications")
    @Operation(summary = "Tout marquer comme lu")
    public ResponseEntity<Map<String, Integer>> markAllNotificationsRead() {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead()));
    }

    // ---------------------------------------------------------------- Reglages et compte

    /**
     * A ne pas confondre avec {@code SettingsController}, qui sert le parametrage metier d'une
     * entreprise cliente — raison sociale, TVA, prefixe de facture.
     */
    @GetMapping("/settings")
    @Tag(name = "Plateforme - Parametres", description = "Reglages de la plateforme et compte proprietaire")
    @Operation(summary = "Reglages courants et identite du compte proprietaire")
    public ResponseEntity<PlatformSettingsResponse> getSettings(@AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(
                platformSettingsService.toResponse(platformSettingsService.getSettings(), owner));
    }

    @PutMapping("/settings")
    @Tag(name = "Plateforme - Parametres")
    @Operation(summary = "Modifier les seuils du tableau de bord",
               description = "Fenetre de renouvellement, alerte de fin d'essai, historique et penalites de sante")
    public ResponseEntity<PlatformSettingsResponse> updateSettings(@Valid @RequestBody PlatformSettingsRequest request,
                                                                   @AuthenticationPrincipal User owner) {
        PlatformSettings settings = platformSettingsService.updateSettings(request);
        return ResponseEntity.ok(platformSettingsService.toResponse(settings, owner));
    }

    /**
     * Modifie le compte proprietaire.
     *
     * Le principal est pris du contexte de securite et non d'un identifiant de l'URL : cette
     * route ne peut donc modifier que le compte de l'appelant, ce qui la met hors de portee
     * d'une manipulation d'identifiant.
     */
    @PatchMapping("/settings/account")
    @Tag(name = "Plateforme - Parametres")
    @Operation(summary = "Modifier son email ou son mot de passe",
               description = "Le mot de passe actuel est exige dans tous les cas")
    public ResponseEntity<PlatformSettingsResponse> updateAccount(
            @Valid @RequestBody PlatformAccountRequest request,
            @AuthenticationPrincipal User owner) {
        User updated = platformSettingsService.updateAccount(owner, request);
        return ResponseEntity.ok(
                platformSettingsService.toResponse(platformSettingsService.getSettings(), updated));
    }

    // ---------------------------------------------------------------- Support

    @GetMapping("/support")
    @Tag(name = "Plateforme - Support", description = "Tickets des entreprises clientes")
    @Operation(summary = "Liste paginee des tickets")
    public ResponseEntity<PageResponse<SupportTicketResponse>> listTickets(
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

    @GetMapping("/support/open-count")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Nombre de tickets encore a traiter")
    public ResponseEntity<Map<String, Long>> openTicketCount() {
        return ResponseEntity.ok(Map.of("count", supportTicketService.countOpen()));
    }

    @GetMapping("/support/{id}")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Detail d'un ticket, fil de discussion compris")
    public ResponseEntity<SupportTicketResponse> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toDetail(supportTicketService.getById(id)));
    }

    /**
     * L'auteur des ecritures est pris du contexte de securite et jamais du corps de la requete :
     * un ticket ou un message doit porter l'identite reelle de celui qui l'a saisi.
     */
    @PostMapping("/support")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Ouvrir un ticket",
               description = "La description devient le premier message du fil")
    public ResponseEntity<SupportTicketResponse> openTicket(@Valid @RequestBody SupportTicketRequest request,
                                                            @AuthenticationPrincipal User operator) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toDetail(supportTicketService.open(request, operator)));
    }

    @PostMapping("/support/{id}/messages")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Ajouter un message ou une note interne")
    public ResponseEntity<SupportTicketResponse> addTicketMessage(@PathVariable Long id,
                                                                  @Valid @RequestBody SupportMessageRequest request,
                                                                  @AuthenticationPrincipal User author) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.addMessage(id, request, author)));
    }

    @PatchMapping("/support/{id}/status")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Changer le statut d'un ticket")
    public ResponseEntity<SupportTicketResponse> changeTicketStatus(@PathVariable Long id,
                                                                    @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.changeStatus(id, body.get("status"))));
    }

    @PatchMapping("/support/{id}/priority")
    @Tag(name = "Plateforme - Support")
    @Operation(summary = "Changer la priorite d'un ticket")
    public ResponseEntity<SupportTicketResponse> changeTicketPriority(@PathVariable Long id,
                                                                      @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                platformMapper.toDetail(supportTicketService.changePriority(id, body.get("priority"))));
    }
}
