package com.suaempresa.backend.controller;

import com.suaempresa.backend.model.Order;
import com.suaempresa.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Equivalente ao antigo controllers/paymentController.js
 * As rotas que antes ficavam em routes/paymentRoutes.js
 * agora sao definidas diretamente aqui, via anotacoes.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // POST /api/payments/{orderId}/process
    @PostMapping("/{orderId}/process")
    public ResponseEntity<Order> processPayment(@PathVariable Long orderId) {
        Order updatedOrder = paymentService.processPayment(orderId);
        return ResponseEntity.ok(updatedOrder);
    }

    // POST /api/payments/{orderId}/cancel
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelPayment(@PathVariable Long orderId) {
        Order updatedOrder = paymentService.cancelPayment(orderId);
        return ResponseEntity.ok(updatedOrder);
    }
}
