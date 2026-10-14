package com.itb.controller;

import com.itb.dto.PaymentRequest;
import com.itb.dto.PaymentResponse;
import com.itb.model.Payment;
import com.itb.service.OrderAccessService;
import com.itb.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderAccessService orderAccessService;

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-Order-Token", required = false) String orderToken
    ) {
        orderAccessService.requireAccess(orderId, orderToken);
        Payment payment = paymentService.getPayment(orderId);
        return ResponseEntity.ok(payment == null ? null : PaymentResponse.from(payment));
    }

    @PostMapping("/{orderId}/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "X-Order-Token", required = false) String orderToken
    ) {
        orderAccessService.requireAccess(orderId, orderToken);
        return ResponseEntity.ok(PaymentResponse.from(paymentService.processPayment(orderId, request)));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@PathVariable Long orderId, Principal principal) {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.confirmPayment(orderId, principal.getName())));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable Long orderId, Principal principal) {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.cancelPayment(orderId, principal.getName())));
    }
}
