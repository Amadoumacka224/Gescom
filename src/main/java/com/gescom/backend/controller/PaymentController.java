package com.gescom.backend.controller;

import com.gescom.backend.dto.payment.PaymentConfirmRequest;
import com.gescom.backend.dto.payment.PaymentIntentCreateRequest;
import com.gescom.backend.dto.payment.PaymentResponse;
import com.gescom.backend.mapper.PaymentMapper;
import com.gescom.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Terminal de paiement carte (Stripe, mode test).
 *
 * Le parcours tient en deux appels — création puis confirmation — plus une porte de sortie
 * (annulation) pour la session abandonnée. Encaisser fait partie du métier du caissier :
 * l'accès suit celui de la facturation, ADMIN et CAISSIER.
 */
@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    public PaymentController(PaymentService paymentService, PaymentMapper paymentMapper) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    /** 1. Ouvre l'intention de paiement et renvoie de quoi la confirmer. */
    @PostMapping("/stripe/intents")
    public ResponseEntity<PaymentResponse> createIntent(@Valid @RequestBody PaymentIntentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMapper.toResponse(
                paymentService.createIntent(request.invoiceId(), request.amount())));
    }

    /** 2. Présente la carte : la facture est encaissée si le prestataire accepte. */
    @PostMapping("/stripe/intents/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirmIntent(@PathVariable Long id,
                                                         @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(paymentMapper.toResponse(
                paymentService.confirmIntent(id, request.paymentMethodId())));
    }

    /** Abandon de la session avant confirmation. */
    @PostMapping("/stripe/intents/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelIntent(@PathVariable Long id) {
        return ResponseEntity.ok(paymentMapper.toResponse(paymentService.cancelIntent(id)));
    }

    @GetMapping("/stripe/intents/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentMapper.toResponse(paymentService.getPayment(id)));
    }

    /** Historique des tentatives d'une facture — les refus y figurent aussi. */
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(invoiceId).stream()
                .map(paymentMapper::toResponse).toList());
    }
}
