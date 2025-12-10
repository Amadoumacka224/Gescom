package com.gescom.backend.controller;

import com.gescom.backend.entity.Client;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.CsvExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/clients")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ClientController {

    @Autowired
    private ClientService clientService;

    @Autowired
    private CsvExportService csvExportService;

    @GetMapping
    public ResponseEntity<List<Client>> getAllClients() {
        List<Client> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Client>> getActiveClients() {
        List<Client> clients = clientService.getActiveClients();
        return ResponseEntity.ok(clients);
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
        List<Client> clients = clientService.getClientsByType(type);
        return ResponseEntity.ok(clients);
    }

    @PostMapping
    public ResponseEntity<?> createClient(@RequestBody Client client) {
        try {
            Client createdClient = clientService.createClient(client);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdClient);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateClient(@PathVariable Long id, @RequestBody Client client) {
        try {
            Client updatedClient = clientService.updateClient(id, client);
            return ResponseEntity.ok(updatedClient);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        try {
            clientService.deleteClient(id);
            return ResponseEntity.ok().body("Client deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateClient(@PathVariable Long id) {
        try {
            clientService.deactivateClient(id);
            return ResponseEntity.ok().body("Client deactivated successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportClients() {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importClients(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide"));
            }

            return ResponseEntity.ok(Map.of(
                "message", "Endpoint d'import disponible. Implémentation complète à venir.",
                "filename", file.getOriginalFilename(),
                "size", file.getSize()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erreur lors de l'import: " + e.getMessage()));
        }
    }
}
