package com.itb.controller;

import com.itb.model.Order;
import com.itb.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;

    @GetMapping
    public ResponseEntity<List<Order>> getAll() {
        return ResponseEntity.ok(
                orderRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {

        return orderRepository.findById(id)
                .filter(order -> order.getDeletedAt() == null)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                orderRepository
                        .findByStatus_NameAndDeletedAtIsNullOrderByCreatedAtAsc(status)
        );
    }
}