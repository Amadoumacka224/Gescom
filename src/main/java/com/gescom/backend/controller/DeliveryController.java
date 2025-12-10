package com.gescom.backend.controller;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.CsvExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/deliveries")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private CsvExportService csvExportService;

    @GetMapping
    public ResponseEntity<List<Delivery>> getAllDeliveries() {
        List<Delivery> deliveries = deliveryService.getAllDeliveries();
        return ResponseEntity.ok(deliveries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Delivery> getDeliveryById(@PathVariable Long id) {
        return deliveryService.getDeliveryById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{deliveryNumber}")
    public ResponseEntity<Delivery> getDeliveryByDeliveryNumber(@PathVariable String deliveryNumber) {
        return deliveryService.getDeliveryByDeliveryNumber(deliveryNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Delivery> getDeliveryByOrder(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Delivery>> getDeliveriesByStatus(@PathVariable Delivery.DeliveryStatus status) {
        List<Delivery> deliveries = deliveryService.getDeliveriesByStatus(status);
        return ResponseEntity.ok(deliveries);
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<Delivery>> getDeliveriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<Delivery> deliveries = deliveryService.getDeliveriesByDateRange(start, end);
        return ResponseEntity.ok(deliveries);
    }

    @PostMapping
    public ResponseEntity<?> createDelivery(@RequestBody Delivery delivery) {
        try {
            Delivery createdDelivery = deliveryService.createDelivery(delivery);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDelivery);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDelivery(@PathVariable Long id, @RequestBody Delivery delivery) {
        try {
            Delivery updatedDelivery = deliveryService.updateDelivery(id, delivery);
            return ResponseEntity.ok(updatedDelivery);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateDeliveryStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            Delivery.DeliveryStatus status = Delivery.DeliveryStatus.valueOf(request.get("status"));
            Delivery updatedDelivery = deliveryService.updateDeliveryStatus(id, status);
            return ResponseEntity.ok(updatedDelivery);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/mark-delivered")
    public ResponseEntity<?> markAsDelivered(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String deliveredBy = request.get("deliveredBy");
            Delivery delivery = deliveryService.markAsDelivered(id, deliveredBy);
            return ResponseEntity.ok(delivery);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/create-invoice")
    public ResponseEntity<?> createInvoiceFromDelivery(@PathVariable Long id) {
        try {
            Invoice invoice = deliveryService.createInvoiceFromDelivery(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteDelivery(@PathVariable Long id) {
        try {
            deliveryService.deleteDelivery(id);
            return ResponseEntity.ok().body("Delivery deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportDeliveries() {
        try {
            List<Delivery> deliveries = deliveryService.getAllDeliveries();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            String[] headers = {
                "ID", "Delivery Number", "Order Number", "Client", "Status", "Contact Name",
                "Contact Phone", "Delivery Address", "City", "Postal Code", "Country",
                "Delivered By", "Scheduled Date", "Delivered Date", "Created At"
            };

            byte[] csvData = csvExportService.exportToCsv(deliveries, headers, delivery -> new String[]{
                csvExportService.toString(delivery.getId()),
                csvExportService.toString(delivery.getDeliveryNumber()),
                delivery.getOrder() != null ? csvExportService.toString(delivery.getOrder().getOrderNumber()) : "",
                delivery.getOrder() != null && delivery.getOrder().getClient() != null ?
                    delivery.getOrder().getClient().getFirstName() + " " + delivery.getOrder().getClient().getLastName() : "",
                csvExportService.toString(delivery.getStatus()),
                csvExportService.toString(delivery.getContactName()),
                csvExportService.toString(delivery.getContactPhone()),
                csvExportService.toString(delivery.getDeliveryAddress()),
                csvExportService.toString(delivery.getDeliveryCity()),
                csvExportService.toString(delivery.getDeliveryPostalCode()),
                csvExportService.toString(delivery.getDeliveryCountry()),
                csvExportService.toString(delivery.getDeliveredBy()),
                delivery.getScheduledDate() != null ? delivery.getScheduledDate().format(formatter) : "",
                delivery.getDeliveredDate() != null ? delivery.getDeliveredDate().format(formatter) : "",
                delivery.getCreatedAt() != null ? delivery.getCreatedAt().format(formatter) : ""
            });

            HttpHeaders headersResponse = new HttpHeaders();
            headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
            headersResponse.setContentDispositionFormData("attachment", "deliveries.csv");

            return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importDeliveries(@RequestParam("file") MultipartFile file) {
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
