package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.dto.client.ClientFilterOptions;
import com.gescom.backend.dto.client.ClientSearchCriteria;
import com.gescom.backend.dto.client.ClientSummary;
import com.gescom.backend.repository.ClientRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service métier des clients (CRUD).
 * Chaque opération est journalisée dans la piste d'audit et l'unicité des identifiants
 * (email / code client selon le cas) est contrôlée avant enregistrement.
 */
@Service
@Transactional
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;
    private final ActivityLogService activityLogService;

    public ClientService(ClientRepository clientRepository, ActivityLogService activityLogService) {
        this.clientRepository = clientRepository;
        this.activityLogService = activityLogService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    /**
     * Page du fichier clients, filtrée et triée en base.
     *
     * L'écran rapatriait tout le fichier puis filtrait, triait et découpait dans le navigateur.
     * Dès lors que la pagination passe au serveur, les critères doivent suivre : filtrer une
     * liste déjà tronquée ne chercherait que dans la page reçue, et l'écran mentirait sur ce
     * qu'il montre.
     */
    @Transactional(readOnly = true)
    public Page<Client> searchClients(ClientSearchCriteria criteria, Pageable pageable) {
        return clientRepository.findAll(buildFilter(criteria), pageable);
    }

    /** Compteurs d'en-tête, agrégés en base sur le fichier entier — voir {@link ClientSummary}. */
    @Transactional(readOnly = true)
    public ClientSummary getSummary() {
        ClientRepository.ClientSummaryView view = clientRepository.summary(
                Client.ClientType.PARTICULIER, Client.ClientType.ENTREPRISE);
        return new ClientSummary(
                view.getTotal(), view.getActive(), view.getIndividuals(), view.getCompanies());
    }

    /** Villes et pays réellement présents, pour les listes déroulantes des filtres. */
    @Transactional(readOnly = true)
    public ClientFilterOptions getFilterOptions() {
        return new ClientFilterOptions(
                clientRepository.findDistinctCities(),
                clientRepository.findDistinctCountries());
    }

    private Specification<Client> buildFilter(ClientSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.search() != null && !criteria.search().isBlank()) {
                // Mêmes champs que l'ancienne recherche du navigateur — nom, prénom, société,
                // e-mail, téléphone, ville —, pour que le passage au serveur ne change rien à
                // ce que l'utilisateur trouve. Le nom complet y était cherché d'un bloc
                // (« Jean Dupont ») : CONCAT le reconstitue, sans quoi une recherche sur deux
                // mots ne ramènerait plus personne.
                String pattern = "%" + criteria.search().toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.concat(cb.concat(
                                cb.coalesce(root.get("firstName"), ""), " "),
                                cb.coalesce(root.get("lastName"), ""))), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("company"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), pattern)));
            }
            if (criteria.type() != null) {
                predicates.add(cb.equal(root.get("type"), criteria.type()));
            }
            if (criteria.active() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.active()));
            }
            if (criteria.city() != null && !criteria.city().isBlank()) {
                predicates.add(cb.equal(root.get("city"), criteria.city()));
            }
            if (criteria.country() != null && !criteria.country().isBlank()) {
                predicates.add(cb.equal(root.get("country"), criteria.country()));
            }
            if (criteria.company() != null && !criteria.company().isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("company"), "")),
                        "%" + criteria.company().toLowerCase().trim() + "%"));
            }
            if (criteria.withEmail() != null) {
                // « Sans e-mail » couvre le nul ET la chaîne vide : les deux se saisissent depuis
                // le formulaire et se ressemblent à l'écran.
                Predicate hasEmail = cb.and(
                        cb.isNotNull(root.get("email")),
                        cb.notEqual(root.get("email"), ""));
                predicates.add(criteria.withEmail() ? hasEmail : cb.not(hasEmail));
            }
            if (criteria.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), criteria.createdFrom().atStartOfDay()));
            }
            if (criteria.createdTo() != null) {
                // Borne haute EXCLUSIVE au lendemain minuit, et non « <= date » : createdAt est
                // un horodatage, un client créé à 14 h le jour de fin serait sinon écarté.
                predicates.add(cb.lessThan(
                        root.get("createdAt"), criteria.createdTo().plusDays(1).atStartOfDay()));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public List<Client> getActiveClients() {
        return clientRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Client> getClientById(Long id) {
        return clientRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Client> getClientByEmail(String email) {
        return clientRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Client> getClientsByType(Client.ClientType type) {
        return clientRepository.findByType(type);
    }

    /**
     * Charge un client en vue d'une demande d'accès RGPD (art. 15).
     * L'export est lui-même journalisé : communiquer des données personnelles est une opération
     * qui doit laisser une trace, au même titre qu'une modification — c'est elle qui atteste
     * ensuite du respect du délai légal d'un mois.
     */
    public Client getClientForExport(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("client", id));

        String clientName = client.getFirstName() + " " + client.getLastName();
        logActivity(ActivityLog.ActionType.EXPORT, "Client", id,
            "Export RGPD des données du client " + clientName);

        return client;
    }

    public Client createClient(Client client) {
        if (client.getEmail() != null && clientRepository.existsByEmail(client.getEmail())) {
            throw new DuplicateResourceException("client", "email", client.getEmail());
        }
        Client savedClient = clientRepository.save(client);

        String clientName = savedClient.getFirstName() + " " + savedClient.getLastName();
        logActivity(ActivityLog.ActionType.CREATE, "Client", savedClient.getId(),
            "Création du client " + clientName);

        return savedClient;
    }

    public Client updateClient(Long id, Client clientDetails) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("client", id));

        // Un email ne peut pas être repris par un autre client (l'enregistrement courant est exclu).
        if (clientDetails.getEmail() != null
                && clientRepository.existsByEmailAndIdNot(clientDetails.getEmail(), id)) {
            throw new DuplicateResourceException("client", "email", clientDetails.getEmail());
        }

        client.setFirstName(clientDetails.getFirstName());
        client.setLastName(clientDetails.getLastName());
        client.setEmail(clientDetails.getEmail());
        client.setPhone(clientDetails.getPhone());
        client.setAddress(clientDetails.getAddress());
        client.setCity(clientDetails.getCity());
        client.setPostalCode(clientDetails.getPostalCode());
        client.setCountry(clientDetails.getCountry());
        client.setCompany(clientDetails.getCompany());
        client.setType(clientDetails.getType());
        client.setActive(clientDetails.getActive());

        Client savedClient = clientRepository.save(client);

        String clientName = savedClient.getFirstName() + " " + savedClient.getLastName();
        logActivity(ActivityLog.ActionType.UPDATE, "Client", savedClient.getId(),
            "Modification du client " + clientName);

        return savedClient;
    }

    public void deleteClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("client", id));
        String clientName = client.getFirstName() + " " + client.getLastName();
        clientRepository.delete(client);

        logActivity(ActivityLog.ActionType.DELETE, "Client", id,
            "Suppression du client " + clientName);
    }

    public void deactivateClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("client", id));
        client.setActive(false);
        clientRepository.save(client);

        String clientName = client.getFirstName() + " " + client.getLastName();
        logActivity(ActivityLog.ActionType.UPDATE, "Client", id,
            "Désactivation du client " + clientName);
    }
}
