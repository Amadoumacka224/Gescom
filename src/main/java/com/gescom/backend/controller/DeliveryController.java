package com.gescom.backend.controller;

import com.gescom.backend.dto.delivery.DeliveryCreateRequest;
import com.gescom.backend.dto.delivery.DeliveryResponse;
import com.gescom.backend.dto.delivery.DeliveryUpdateRequest;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.DeliveryService;
import jakarta.validation.Valid;
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
    private final OrderRepository orderRepository;

    public DeliveryController(DeliveryService deliveryService,
                              CsvExportService csvExportService,
                              OrderRepository orderRepository) {
        this.deliveryService = deliveryService;
        this.csvExportService = csvExportService;
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries().stream()
                .map(DeliveryResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getDeliveryById(@PathVariable Long id) {
        return deliveryService.getDeliveryById(id)
                .map(DeliveryResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{deliveryNumber}")
    public ResponseEntity<DeliveryResponse> getDeliveryByDeliveryNumber(@PathVariable String deliveryNumber) {
        return deliveryService.getDeliveryByDeliveryNumber(deliveryNumber)
                .map(DeliveryResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getDeliveryByOrder(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrder(orderId)
                .map(DeliveryResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByStatus(@PathVariable Delivery.DeliveryStatus status) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByStatus(status).stream()
                .map(DeliveryResponse::from).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByDateRange(start, end).stream()
                .map(DeliveryResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> createDelivery(@Valid @RequestBody DeliveryCreateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande", request.orderId()));

        Delivery delivery = new Delivery();
        delivery.setOrder(order);
        delivery.setDeliveryAddress(request.deliveryAddress());
        delivery.setDeliveryCity(request.deliveryCity());
        delivery.setDeliveryPostalCode(request.deliveryPostalCode());
        delivery.setDeliveryCountry(request.deliveryCountry());
        delivery.setContactName(request.contactName());
        delivery.setContactPhone(request.contactPhone());
        delivery.setScheduledDate(request.scheduledDate());
        if (request.status() != null) {
            delivery.setStatus(request.status());
        }
        delivery.setNotes(request.notes());

        Delivery created = deliveryService.createDelivery(delivery);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeliveryResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeliveryResponse> updateDelivery(@PathVariable Long id,
                                                           @Valid @RequestBody DeliveryUpdateRequest request) {
        Delivery patch = new Delivery();
        patch.setDeliveryAddress(request.deliveryAddress());
        patch.setDeliveryCity(request.deliveryCity());
        patch.setDeliveryPostalCode(request.deliveryPostalCode());
        patch.setDeliveryCountry(request.deliveryCountry());
        patch.setContactName(request.contactName());
        patch.setContactPhone(request.contactPhone());
        patch.setScheduledDate(request.scheduledDate());
        patch.setStatus(request.status());
        patch.setNotes(request.notes());

        return ResponseEntity.ok(DeliveryResponse.from(deliveryService.updateDelivery(id, patch)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DeliveryResponse> updateDeliveryStatus(@PathVariable Long id,
                                                                  @RequestBody Map<String, String> request) {
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
        return ResponseEntity.ok(DeliveryResponse.from(deliveryService.updateDeliveryStatus(id, status)));
    }

    @PatchMapping("/{id}/mark-delivered")
    public ResponseEntity<DeliveryResponse> markAsDelivered(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String deliveredBy = request.get("deliveredBy");
        return ResponseEntity.ok(DeliveryResponse.from(deliveryService.markAsDelivered(id, deliveredBy)));
    }

    @PostMapping("/{id}/create-invoice")
    public ResponseEntity<InvoiceResponse> createInvoiceFromDelivery(@PathVariable Long id) {
        Invoice invoice = deliveryService.createInvoiceFromDelivery(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceResponse.from(invoice));
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
