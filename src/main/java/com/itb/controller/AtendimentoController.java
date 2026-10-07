package com.itb.controller;

import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import com.itb.dto.AtendimentoStatusRequest;
import com.itb.service.AtendimentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/atendimentos")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;

    public AtendimentoController(AtendimentoService atendimentoService) {
        this.atendimentoService = atendimentoService;
    }

    @GetMapping
    public List<AtendimentoResponse> listar() {
        return atendimentoService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AtendimentoResponse criar(
            @Valid @RequestBody AtendimentoCreateRequest request
    ) {
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
