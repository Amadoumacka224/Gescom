package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public Client createClient(Client client) {
        if (client.getEmail() != null && clientRepository.existsByEmail(client.getEmail())) {
            throw new DuplicateResourceException("Client", "email", client.getEmail());
        }
        Client savedClient = clientRepository.save(client);

        String clientName = savedClient.getFirstName() + " " + savedClient.getLastName();
        logActivity(ActivityLog.ActionType.CREATE, "Client", savedClient.getId(),
            "Création du client " + clientName);

        return savedClient;
    }

    public Client updateClient(Long id, Client clientDetails) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));

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
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
        String clientName = client.getFirstName() + " " + client.getLastName();
        clientRepository.delete(client);

        logActivity(ActivityLog.ActionType.DELETE, "Client", id,
            "Suppression du client " + clientName);
    }

    public void deactivateClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
        client.setActive(false);
        clientRepository.save(client);

        String clientName = client.getFirstName() + " " + client.getLastName();
        logActivity(ActivityLog.ActionType.UPDATE, "Client", id,
            "Désactivation du client " + clientName);
    }
}
