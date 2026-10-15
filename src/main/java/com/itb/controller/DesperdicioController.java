package com.itb.controller;

import com.itb.dto.DesperdicioCreateRequest;
import com.itb.dto.DesperdicioResponse;
import com.itb.dto.DesperdicioResumoResponse;
import com.itb.service.DesperdicioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/desperdicios")
public class DesperdicioController {

    private final DesperdicioService service;

    public DesperdicioController(DesperdicioService service) {
        this.service = service;
    }

    @GetMapping
    public List<DesperdicioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/resumo")
    public DesperdicioResumoResponse resumo() {
        return service.resumo();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DesperdicioResponse registrar(@Valid @RequestBody DesperdicioCreateRequest request, Principal principal) {
        return service.registrar(request, principal.getName());
    }
}
