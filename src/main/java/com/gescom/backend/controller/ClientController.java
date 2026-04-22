package com.gescom.backend.controller;

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
import java.util.Map;

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

    @GetMapping
    public ResponseEntity<List<Client>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Client>> getActiveClients() {
        return ResponseEntity.ok(clientService.getActiveClients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClientById(@PathVariable Long id) {
        return clientService.getClientById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Client> getClientByEmail(@PathVariable String email) {
        return clientService.getClientByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Client>> getClientsByType(@PathVariable Client.ClientType type) {
        return ResponseEntity.ok(clientService.getClientsByType(type));
    }

    @PostMapping
    public ResponseEntity<Client> createClient(@Valid @RequestBody Client client) {
        Client createdClient = clientService.createClient(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdClient);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> updateClient(@PathVariable Long id, @Valid @RequestBody Client client) {
        return ResponseEntity.ok(clientService.updateClient(id, client));
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
