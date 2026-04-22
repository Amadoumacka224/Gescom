package com.gescom.backend.controller;

import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.CsvExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deliveries")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final CsvExportService csvExportService;

    public DeliveryController(DeliveryService deliveryService, CsvExportService csvExportService) {
        this.deliveryService = deliveryService;
        this.csvExportService = csvExportService;
    }

    @GetMapping
    public ResponseEntity<List<Delivery>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries());
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
        return ResponseEntity.ok(deliveryService.getDeliveriesByStatus(status));
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<Delivery>> getDeliveriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByDateRange(start, end));
    }

    @PostMapping
    public ResponseEntity<Delivery> createDelivery(@RequestBody Delivery delivery) {
        Delivery createdDelivery = deliveryService.createDelivery(delivery);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDelivery);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Delivery> updateDelivery(@PathVariable Long id, @RequestBody Delivery delivery) {
        return ResponseEntity.ok(deliveryService.updateDelivery(id, delivery));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Delivery> updateDeliveryStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String raw = request != null ? request.get("status") : null;
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Le champ 'status' est obligatoire");
        }
        Delivery.DeliveryStatus status;
        try {
            status = Delivery.DeliveryStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Statut de livraison inconnu : " + raw);
        }
        return ResponseEntity.ok(deliveryService.updateDeliveryStatus(id, status));
    }

    @PatchMapping("/{id}/mark-delivered")
    public ResponseEntity<Delivery> markAsDelivered(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String deliveredBy = request.get("deliveredBy");
        return ResponseEntity.ok(deliveryService.markAsDelivered(id, deliveredBy));
    }

    @PostMapping("/{id}/create-invoice")
    public ResponseEntity<Invoice> createInvoiceFromDelivery(@PathVariable Long id) {
        Invoice invoice = deliveryService.createInvoiceFromDelivery(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDelivery(@PathVariable Long id) {
        deliveryService.deleteDelivery(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportDeliveries() {
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
    }
}
