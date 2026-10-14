package com.itb.controller;

import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import com.itb.dto.AtendimentoStatusRequest;
import com.itb.service.AtendimentoService;
import com.itb.service.OrderAccessService;
import com.itb.service.TableAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/atendimentos")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;
    private final OrderAccessService orderAccessService;
    private final TableAccessService tableAccessService;

    public AtendimentoController(AtendimentoService atendimentoService, OrderAccessService orderAccessService,
            TableAccessService tableAccessService) {
        this.atendimentoService = atendimentoService;
        this.orderAccessService = orderAccessService;
        this.tableAccessService = tableAccessService;
    }

    @GetMapping
    public List<AtendimentoResponse> listar() {
        return atendimentoService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AtendimentoResponse criar(
            @Valid @RequestBody AtendimentoCreateRequest request,
            @RequestHeader(value = "X-Order-Token", required = false) String token,
            @RequestHeader(value = "X-Table-Token", required = false) String tableToken
    ) {
        if (request.pedidoId() != null) {
            var order = orderAccessService.requireAccess(request.pedidoId(), token);
            if (order.getMesa() == null || !request.mesaId().equals(order.getMesa().getId())) {
                throw new IllegalArgumentException("Pedido não encontrado para esta mesa.");
            }
        } else {
            tableAccessService.requireWriteAccess(request.mesaId(), tableToken);
        }
        return atendimentoService.criar(request);
    }

    @PatchMapping("/{id}/status")
    public AtendimentoResponse atualizarStatus(
            @PathVariable Long id,
            @Valid @RequestBody AtendimentoStatusRequest request
    ) {
        return atendimentoService.atualizarStatus(id, request.status());
    }
}
