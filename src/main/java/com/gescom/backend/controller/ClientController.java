package com.gescom.backend.controller;

import com.gescom.backend.dto.client.ClientDataExport;
import com.gescom.backend.dto.client.ClientRequest;
import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.ReferenceMapper;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ClientController {

    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClientService clientService;
    private final OrderService orderService;
    private final CsvExportService csvExportService;
    private final ReferenceMapper referenceMapper;

    public ClientController(ClientService clientService, OrderService orderService,
                            CsvExportService csvExportService, ReferenceMapper referenceMapper) {
        this.clientService = clientService;
        this.orderService = orderService;
        this.csvExportService = csvExportService;
        this.referenceMapper = referenceMapper;
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients().stream()
                .map(referenceMapper::toResponse).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ClientResponse>> getActiveClients() {
        return ResponseEntity.ok(clientService.getActiveClients().stream()
                .map(referenceMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(@PathVariable Long id) {
        return clientService.getClientById(id)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("client", id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ClientResponse> getClientByEmail(@PathVariable String email) {
        return clientService.getClientByEmail(email)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("client", "email", email));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ClientResponse>> getClientsByType(@PathVariable Client.ClientType type) {
        return ResponseEntity.ok(clientService.getClientsByType(type).stream()
                .map(referenceMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        Client created = clientService.createClient(referenceMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(@PathVariable Long id,
                                                       @Valid @RequestBody ClientRequest request) {
        Client details = referenceMapper.toEntity(request);
        return ResponseEntity.ok(referenceMapper.toResponse(clientService.updateClient(id, details)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateClient(@PathVariable Long id) {
        clientService.deactivateClient(id);
        return ResponseEntity.noContent().build();
    }

    /** Export CSV de l'ensemble du fichier clients. Usage interne — voir /{id}/export pour un DSAR. */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportClients() {
        List<Client> clients = clientService.getAllClients();

        String[] headers = {
            "ID", "First Name", "Last Name", "Email", "Phone", "Address",
            "City", "Postal Code", "Country", "Company", "Type", "Active", "Created At"
        };

        byte[] csvData = csvExportService.exportToCsv(clients, headers, client -> new String[]{
            csvExportService.toString(client.getId()),
            csvExportService.toString(client.getFirstName()),
            csvExportService.toString(client.getLastName()),
            csvExportService.toString(client.getEmail()),
            csvExportService.toString(client.getPhone()),
            csvExportService.toString(client.getAddress()),
            csvExportService.toString(client.getCity()),
            csvExportService.toString(client.getPostalCode()),
            csvExportService.toString(client.getCountry()),
            csvExportService.toString(client.getCompany()),
            csvExportService.toString(client.getType()),
            csvExportService.toString(client.getActive()),
            client.getCreatedAt() != null ? client.getCreatedAt().format(EXPORT_DATE) : ""
        });

        HttpHeaders headersResponse = new HttpHeaders();
        headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
        headersResponse.setContentDispositionFormData("attachment", "clients.csv");

        return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
    }

    /**
     * Demande d'accès RGPD (art. 15) : toutes les données détenues sur un client donné.
     * À ne pas confondre avec /export sans identifiant, qui sort le fichier clients entier —
     * une réponse DSAR est individuelle et ciblée, et c'est bien l'inverse.
     *
     * JSON par défaut ; ?format=csv rend le même contenu à plat, pour un demandeur qui préfère
     * l'ouvrir dans un tableur. Réservé à l'ADMIN, et journalisé côté service.
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> exportClientData(@PathVariable Long id,
                                              @RequestParam(defaultValue = "json") String format) {
        Client client = clientService.getClientForExport(id);
        List<Order> orders = orderService.getOrdersByClient(id);
        ClientDataExport export = referenceMapper.toDataExport(client, orders);

        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(export);
        }

        byte[] csvData = csvExportService.exportToCsv(
                toCsvRows(export), new String[]{"Section", "Champ", "Valeur"}, row -> row);

        HttpHeaders headersResponse = new HttpHeaders();
        headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
        headersResponse.setContentDispositionFormData("attachment", "client-" + id + "-donnees.csv");

        return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
    }

    // Mise à plat de l'export DSAR : une ligne par donnée, puis une ligne par commande.
    private List<String[]> toCsvRows(ClientDataExport export) {
        ClientResponse c = export.client();
        List<String[]> rows = new ArrayList<>(List.of(
                identity("Identifiant", c.id()),
                identity("Prénom", c.firstName()),
                identity("Nom", c.lastName()),
                identity("Email", c.email()),
                identity("Téléphone", c.phone()),
                identity("Adresse", c.address()),
                identity("Ville", c.city()),
                identity("Code postal", c.postalCode()),
                identity("Pays", c.country()),
                identity("Société", c.company()),
                identity("Type", c.type()),
                identity("Actif", c.active()),
                identity("Créé le", c.createdAt() != null ? c.createdAt().format(EXPORT_DATE) : ""),
                identity("Modifié le", c.updatedAt() != null ? c.updatedAt().format(EXPORT_DATE) : "")
        ));

        for (ClientDataExport.OrderHistoryEntry order : export.orders()) {
            String detail = (order.createdAt() != null ? order.createdAt().format(EXPORT_DATE) : "")
                    + " - " + order.status() + " - " + csvExportService.toString(order.finalAmount());
            rows.add(new String[]{"Commandes", csvExportService.toString(order.orderNumber()), detail});
        }

        return rows;
    }

    private String[] identity(String field, Object value) {
        return new String[]{"Identité", field, csvExportService.toString(value)};
    }
}
