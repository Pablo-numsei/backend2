package com.itb.controller;
import com.itb.dto.AvancarStatusRequest;
import com.itb.dto.PedidoCreateRequest;
import com.itb.model.Order;
import com.itb.repository.OrderRepository;
import com.itb.service.PedidoService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final PedidoService pedidoService;

    // LISTAR TODOS OS PEDIDOS
    @GetMapping
    public ResponseEntity<List<Order>> getAll() {

        return ResponseEntity.ok(
                orderRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()
        );
    }

    // BUSCAR PEDIDO POR ID
   @GetMapping("/{id}")
public ResponseEntity<Order> getById(
        @PathVariable("id") Long id) {

    return orderRepository.findById(id)
            .filter(order -> order.getDeletedAt() == null)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}

    // BUSCAR PEDIDOS POR STATUS
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                orderRepository
                        .findByStatus_NameAndDeletedAtIsNullOrderByCreatedAtAsc(
                                status
                        )
        );
    }

    // CRIAR PEDIDO
    @PostMapping
    public ResponseEntity<Order> criarPedido(
            @Valid @RequestBody PedidoCreateRequest request) {

        Order pedido = pedidoService.criarPedido(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pedido);
    }



// AVANÇAR STATUS DO PEDIDO
@PatchMapping("/{id}/status")
public ResponseEntity<Order> avancarStatus(
        @PathVariable("id") Long id,
        @Valid @RequestBody AvancarStatusRequest request) {

    Order pedidoAtualizado = pedidoService.avancarStatus(
            id,
            request
    );

    return ResponseEntity.ok(pedidoAtualizado);
}

}