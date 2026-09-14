package com.itb.controller;

import com.itb.model.StatusPedido;
import com.itb.repository.StatusPedidoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/status-pedidos")
@RequiredArgsConstructor
public class StatusPedidoController {

    private final StatusPedidoRepository statusPedidoRepository;

    @GetMapping
    public ResponseEntity<List<StatusPedido>> findAll() {
        return ResponseEntity.ok(statusPedidoRepository.findAll());
    }
}