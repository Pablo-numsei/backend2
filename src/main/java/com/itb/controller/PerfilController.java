package com.itb.controller;

import com.itb.model.Perfil;
import com.itb.repository.PerfilRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/perfis")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilRepository perfilRepository;

    @GetMapping
    public ResponseEntity<List<Perfil>> getAll() {
        return ResponseEntity.ok(perfilRepository.findAll());
    }
}
