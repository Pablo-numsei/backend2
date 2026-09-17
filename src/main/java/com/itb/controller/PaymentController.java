package com.itb.controller;

import com.itb.dto.PaymentRequest;
import com.itb.model.Payment;
import com.itb.service.PaymentService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // POST /api/payments/{orderId}/process
    @PostMapping("/{orderId}/process")
    public ResponseEntity<Payment> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request
    ) {

        Payment payment =
                paymentService.processPayment(orderId, request);

        return ResponseEntity.ok(payment);
    }

    // POST /api/payments/{orderId}/cancel
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Payment> cancelPayment(
            @PathVariable Long orderId
    ) {

        Payment payment =
                paymentService.cancelPayment(orderId);

        return ResponseEntity.ok(payment);
    }
}