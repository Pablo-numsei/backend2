package com.itb.controller;

import com.itb.dto.ClienteCadastroRequest;
import com.itb.dto.ClienteCadastroResponse;
import com.itb.service.ClienteCadastroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteCadastroController {
    private final ClienteCadastroService clienteCadastroService;

    @PostMapping("/cadastro")
    public ResponseEntity<ClienteCadastroResponse> cadastrar(@Valid @RequestBody ClienteCadastroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteCadastroService.cadastrar(request));
    }
}
