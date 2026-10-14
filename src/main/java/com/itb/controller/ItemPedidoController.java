package com.itb.controller;

import com.itb.dto.ItemPedidoResponse;
import com.itb.service.OrderAccessService;
import com.itb.repository.ItemPedidoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itens-pedido")
@RequiredArgsConstructor
public class ItemPedidoController {

    private final ItemPedidoRepository itemPedidoRepository;
    private final OrderAccessService orderAccessService;

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<List<ItemPedidoResponse>> getByPedido(
            @PathVariable Long pedidoId,
            @RequestHeader(value = "X-Order-Token", required = false) String token) {

        orderAccessService.requireAccess(pedidoId, token);
        return ResponseEntity.ok(itemPedidoRepository.findByPedido_Id(pedidoId)
                .stream().map(ItemPedidoResponse::from).toList());
    }
}
