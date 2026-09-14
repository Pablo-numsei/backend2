package com.itb.controller;

import com.itb.model.ItemPedido;
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

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<List<ItemPedido>> getByPedido(
            @PathVariable Long pedidoId) {

        return ResponseEntity.ok(
                itemPedidoRepository.findByPedido_Id(pedidoId)
        );
    }
}