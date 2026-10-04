package com.gescom.backend.controller;

import com.gescom.backend.dto.payment.PaymentConfirmRequest;
import com.gescom.backend.dto.payment.PaymentIntentCreateRequest;
import com.gescom.backend.dto.payment.PaymentResponse;
import com.gescom.backend.mapper.SalesMapper;
import com.gescom.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Terminal de paiement par carte (Stripe, mode test).
 *
 * Deux appels (création puis confirmation), plus une annulation si on abandonne la session.
 * Encaisser fait partie du travail du caissier : mêmes droits que pour la facturation,
 * ADMIN et CAISSIER.
 */
@Tag(name = "Paiements par carte", description = "Terminal Stripe, encaissement des factures")
@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class PaymentController {

    private final PaymentService paymentService;
    private final SalesMapper salesMapper;

    public PaymentController(PaymentService paymentService, SalesMapper salesMapper) {
        this.paymentService = paymentService;
        this.salesMapper = salesMapper;
    }

    /** 1. Crée l'intention de paiement et renvoie ce qu'il faut pour la confirmer. */
    @PostMapping("/stripe/intents")
    public ResponseEntity<PaymentResponse> createIntent(@Valid @RequestBody PaymentIntentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesMapper.toResponse(
                paymentService.createIntent(request.invoiceId(), request.amount())));
    }

    /** 2. Envoie la carte : la facture est payée si Stripe accepte. */
    @PostMapping("/stripe/intents/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirmIntent(@PathVariable Long id,
                                                         @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(salesMapper.toResponse(
                paymentService.confirmIntent(id, request.paymentMethodId())));
    }

    /** Annule la session avant la confirmation. */
    @PostMapping("/stripe/intents/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelIntent(@PathVariable Long id) {
        return ResponseEntity.ok(salesMapper.toResponse(paymentService.cancelIntent(id)));
    }

    @GetMapping("/stripe/intents/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(salesMapper.toResponse(paymentService.getPayment(id)));
    }

    /** Historique des tentatives sur une facture (y compris les refus). */
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(invoiceId).stream()
                .map(salesMapper::toResponse).toList());
    }
}
