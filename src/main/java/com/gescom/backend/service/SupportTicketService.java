package com.gescom.backend.service;

import com.gescom.backend.dto.platform.*;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.PlatformNotification;
import com.gescom.backend.entity.SupportTicket;
import com.gescom.backend.entity.SupportTicketMessage;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.SupportTicketRepository;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Billetterie du support.
 *
 * Le ticket est ouvert par l'operateur a partir d'un appel ou d'un courriel : GESCOM garde
 * la trace et le suivi, le canal reste humain. Rien n'est envoye au client — le fil est un
 * registre, pas une messagerie, et {@code internal} prepare la distinction du jour ou un
 * envoi automatique sera branche.
 */
@Service
@Transactional
public class SupportTicketService {

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final SupportTicketRepository ticketRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PlatformNotificationService notificationService;

    public SupportTicketService(SupportTicketRepository ticketRepository,
                                CompanyRepository companyRepository,
                                UserRepository userRepository,
                                PlatformNotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ── Lectures ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SupportTicket> search(String status, String priority, Long companyId,
                                      String search, Pageable pageable) {
        return ticketRepository.findAll(filter(
                parse(SupportTicket.TicketStatus.class, status, "support.status.invalid"),
                parse(SupportTicket.TicketPriority.class, priority, "support.priority.invalid"),
                companyId, search), pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getById(Long id) {
        return ticketRepository.findWithMessagesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ticket", id));
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return ticketRepository.countByStatusIn(SupportTicket.OPEN_STATUSES);
    }

    // ── Ecritures ────────────────────────────────────────────────────────────

    public SupportTicket open(SupportTicketRequest request, User operator) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));

        SupportTicket ticket = new SupportTicket();
        ticket.setReference(nextReference());
        ticket.setCompany(company);
        ticket.setSubject(request.subject());
        ticket.setPriority(parse(SupportTicket.TicketPriority.class, request.priority(),
                "support.priority.invalid"));
        ticket.setCategory(parse(SupportTicket.TicketCategory.class, request.category(),
                "support.category.invalid"));
        ticket.setOpenedBy(operator);
        ticket.setStatus(SupportTicket.TicketStatus.OPEN);

        if (request.contactUserId() != null) {
            User contact = userRepository.findById(request.contactUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("user", request.contactUserId()));
            // Un interlocuteur d'une autre entreprise serait une erreur de saisie qui
            // brouillerait le suivi : on la refuse plutot que de la consigner.
            if (contact.getOwnerCompany() == null
                    || !contact.getOwnerCompany().getId().equals(company.getId())) {
                throw BusinessException.of("support.contact.mismatch",
                        "L'interlocuteur choisi n'appartient pas a cette entreprise");
            }
            ticket.setContactUser(contact);
        }

        // La description devient le premier message : un seul endroit ou lire le contenu.
        SupportTicketMessage first = new SupportTicketMessage();
        first.setTicket(ticket);
        first.setAuthor(operator);
        first.setBody(request.description());
        first.setInternal(false);
        ticket.getMessages().add(first);

        SupportTicket saved = ticketRepository.save(ticket);

        notificationService.record("SUPPORT_TICKET_OPENED",
                severityOf(saved.getPriority()),
                "Ticket " + saved.getReference() + " ouvert",
                company.getName() + " — " + saved.getSubject(),
                company, "SupportTicket", saved.getId());

        return saved;
    }

    public SupportTicket addMessage(Long ticketId, SupportMessageRequest request, User author) {
        SupportTicket ticket = getById(ticketId);
        if (ticket.getStatus() == SupportTicket.TicketStatus.CLOSED) {
            throw BusinessException.of("support.ticket.closed",
                    "Ce ticket est clos : rouvrez-le pour y repondre");
        }
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setBody(request.body());
        message.setInternal(Boolean.TRUE.equals(request.internal()));
        ticket.getMessages().add(message);

        // Repondre a un ticket en attente le remet naturellement en traitement ; une note
        // interne, elle, ne change rien a l'etat du dossier vis-a-vis du client.
        if (!message.isInternal() && ticket.getStatus() == SupportTicket.TicketStatus.OPEN) {
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
        }
        return ticketRepository.save(ticket);
    }

    public SupportTicket changeStatus(Long ticketId, String status) {
        SupportTicket ticket = getById(ticketId);
        SupportTicket.TicketStatus target = parse(SupportTicket.TicketStatus.class, status,
                "support.status.invalid");
        if (target == null) {
            throw BusinessException.of("support.status.invalid", "Statut de ticket invalide");
        }

        ticket.setStatus(target);
        // Les horodatages suivent le statut plutot que d'etre saisis : ils servent aux delais
        // de traitement, et une date renseignee a la main s'en ecarte vite.
        ticket.setResolvedAt(target == SupportTicket.TicketStatus.RESOLVED
                || target == SupportTicket.TicketStatus.CLOSED
                ? (ticket.getResolvedAt() != null ? ticket.getResolvedAt() : LocalDateTime.now())
                : null);
        ticket.setClosedAt(target == SupportTicket.TicketStatus.CLOSED ? LocalDateTime.now() : null);

        return ticketRepository.save(ticket);
    }

    public SupportTicket changePriority(Long ticketId, String priority) {
        SupportTicket ticket = getById(ticketId);
        SupportTicket.TicketPriority target = parse(SupportTicket.TicketPriority.class, priority,
                "support.priority.invalid");
        if (target == null) {
            throw BusinessException.of("support.priority.invalid", "Priorite invalide");
        }
        ticket.setPriority(target);
        return ticketRepository.save(ticket);
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private Specification<SupportTicket> filter(SupportTicket.TicketStatus status,
                                                SupportTicket.TicketPriority priority,
                                                Long companyId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (companyId != null) predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern),
                        cb.like(cb.lower(root.get("reference")), pattern),
                        cb.like(cb.lower(root.get("company").get("name")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Reference lisible : TK-<periode>-<compteur du mois>. */
    private String nextReference() {
        String prefix = "TK-" + LocalDateTime.now().format(PERIOD);
        int sequence = 1;
        String candidate = prefix + "-" + String.format("%03d", sequence);
        while (ticketRepository.existsByReference(candidate)) {
            candidate = prefix + "-" + String.format("%03d", ++sequence);
        }
        return candidate;
    }

    /** Une demande urgente merite une notification critique ; les autres restent informatives. */
    private PlatformNotification.Severity severityOf(SupportTicket.TicketPriority priority) {
        return switch (priority) {
            case URGENT -> PlatformNotification.Severity.CRITICAL;
            case HIGH -> PlatformNotification.Severity.WARNING;
            default -> PlatformNotification.Severity.INFO;
        };
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String key) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.of(key, "Valeur invalide : " + value, value);
        }
    }
}
