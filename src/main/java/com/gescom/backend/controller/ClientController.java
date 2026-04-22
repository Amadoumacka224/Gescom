package com.gescom.backend.controller;

import com.gescom.backend.dto.client.ClientRequest;
import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.entity.Client;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.CsvExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ClientController {

    private final ClientService clientService;
    private final CsvExportService csvExportService;

    public ClientController(ClientService clientService, CsvExportService csvExportService) {
        this.clientService = clientService;
        this.csvExportService = csvExportService;
    }

    private Client applyRequest(Client target, ClientRequest request) {
        target.setFirstName(request.firstName());
        target.setLastName(request.lastName());
        target.setEmail(request.email());
        target.setPhone(request.phone());
        target.setAddress(request.address());
        target.setCity(request.city());
        target.setPostalCode(request.postalCode());
        target.setCountry(request.country());
        target.setCompany(request.company());
        target.setType(request.type());
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return target;
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients().stream()
                .map(ClientResponse::from).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ClientResponse>> getActiveClients() {
        return ResponseEntity.ok(clientService.getActiveClients().stream()
                .map(ClientResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(@PathVariable Long id) {
        return clientService.getClientById(id)
                .map(ClientResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ClientResponse> getClientByEmail(@PathVariable String email) {
        return clientService.getClientByEmail(email)
                .map(ClientResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ClientResponse>> getClientsByType(@PathVariable Client.ClientType type) {
        return ResponseEntity.ok(clientService.getClientsByType(type).stream()
                .map(ClientResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        Client client = applyRequest(new Client(), request);
        Client created = clientService.createClient(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(@PathVariable Long id,
                                                       @Valid @RequestBody ClientRequest request) {
        Client details = applyRequest(new Client(), request);
        return ResponseEntity.ok(ClientResponse.from(clientService.updateClient(id, details)));
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

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportClients() {
        List<Client> clients = clientService.getAllClients();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            client.getCreatedAt() != null ? client.getCreatedAt().format(formatter) : ""
        });

        HttpHeaders headersResponse = new HttpHeaders();
        headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
        headersResponse.setContentDispositionFormData("attachment", "clients.csv");

        return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
    }
}
