package com.gescom.backend.controller;

import com.gescom.backend.dto.delivery.DeliveryCreateRequest;
import com.gescom.backend.dto.delivery.DeliveryResponse;
import com.gescom.backend.dto.delivery.DeliveryStatusUpdateRequest;
import com.gescom.backend.dto.delivery.DeliveryUpdateRequest;
import com.gescom.backend.dto.delivery.MarkDeliveredRequest;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.DeliveryMapper;
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

@RestController
@RequestMapping("/api/deliveries")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final CsvExportService csvExportService;
    private final DeliveryMapper deliveryMapper;

    public DeliveryController(DeliveryService deliveryService,
                              CsvExportService csvExportService,
                              DeliveryMapper deliveryMapper) {
        this.deliveryService = deliveryService;
        this.csvExportService = csvExportService;
        this.deliveryMapper = deliveryMapper;
    }

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries().stream()
                .map(deliveryMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getDeliveryById(@PathVariable Long id) {
        return deliveryService.getDeliveryById(id)
                .map(deliveryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));
    }

    @GetMapping("/number/{deliveryNumber}")
    public ResponseEntity<DeliveryResponse> getDeliveryByDeliveryNumber(@PathVariable String deliveryNumber) {
        return deliveryService.getDeliveryByDeliveryNumber(deliveryNumber)
                .map(deliveryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", "number", deliveryNumber));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getDeliveryByOrder(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrder(orderId)
                .map(deliveryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", "order", String.valueOf(orderId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByStatus(@PathVariable Delivery.DeliveryStatus status) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByStatus(status).stream()
                .map(deliveryMapper::toResponse).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<DeliveryResponse>> getDeliveriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(deliveryService.getDeliveriesByDateRange(start, end).stream()
                .map(deliveryMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> createDelivery(@Valid @RequestBody DeliveryCreateRequest request) {
        Delivery created = deliveryService.createDelivery(deliveryMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeliveryResponse> updateDelivery(@PathVariable Long id,
                                                           @Valid @RequestBody DeliveryUpdateRequest request) {
        Delivery patch = deliveryMapper.applyUpdate(new Delivery(), request);
        return ResponseEntity.ok(deliveryMapper.toResponse(deliveryService.updateDelivery(id, patch)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DeliveryResponse> updateDeliveryStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        Delivery.DeliveryStatus status;
        try {
            status = Delivery.DeliveryStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of("delivery.status.unknown",
                    "Statut de livraison inconnu : " + request.status(), request.status());
        }
        return ResponseEntity.ok(deliveryMapper.toResponse(deliveryService.updateDeliveryStatus(id, status)));
    }

    @PatchMapping("/{id}/mark-delivered")
    public ResponseEntity<DeliveryResponse> markAsDelivered(@PathVariable Long id,
                                                            @Valid @RequestBody MarkDeliveredRequest request) {
        return ResponseEntity.ok(deliveryMapper.toResponse(deliveryService.markAsDelivered(id, request.deliveredBy())));
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
