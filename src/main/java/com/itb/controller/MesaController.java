package com.itb.controller;

import com.itb.model.Mesa;
import com.itb.repository.MesaRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {

    private final MesaRepository mesaRepository;
    private final EntityManager entityManager;

    // =========================
    // LISTAR MESAS
    // =========================
    @GetMapping
    public ResponseEntity<List<Mesa>> getAll() {

        return ResponseEntity.ok(
                mesaRepository.findAll()
        );
    }

    // =========================
    // BUSCAR MESA POR ID
    // =========================
    @GetMapping("/{id}")
    public ResponseEntity<Mesa> getById(
            @PathVariable Long id
    ) {

        return mesaRepository
                .findById(id)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // CRIAR MESA
    // =========================
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(
            @Valid @RequestBody Mesa mesa
    ) {

        if (mesaRepository.existsByNumber(
                mesa.getNumber()
        )) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            "Já existe uma mesa com esse número."
                    );
        }

        if (mesaRepository.existsByQrCode(
                mesa.getQrCode()
        )) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            "Esse QR Code já está sendo utilizado."
                    );
        }

        Mesa saved =
                mesaRepository.saveAndFlush(mesa);

        entityManager.refresh(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }

    // =========================
    // ATUALIZAR MESA
    // =========================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody Mesa mesa
    ) {

        return mesaRepository
                .findById(id)
                .map(existing -> {

                    if (mesaRepository
                            .existsByNumberAndIdNot(
                                    mesa.getNumber(),
                                    id
                            )) {

                        return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(
                                        "Já existe outra mesa com esse número."
                                );
                    }

                    if (mesaRepository
                            .existsByQrCodeAndIdNot(
                                    mesa.getQrCode(),
                                    id
                            )) {

                        return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(
                                        "Esse QR Code já está sendo utilizado por outra mesa."
                                );
                    }

                    existing.setNumber(
                            mesa.getNumber()
                    );

                    existing.setQrCode(
                            mesa.getQrCode()
                    );

                    Mesa saved =
                            mesaRepository.save(existing);

                    return ResponseEntity
                            .ok(saved);
                })
                .orElse(
                        ResponseEntity
                                .notFound()
                                .build()
                );
    }

    // =========================
    // DESATIVAR MESA
    // =========================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id
    ) {

        return mesaRepository
                .findById(id)
                .map(mesa -> {

                    mesa.setActive(false);

                    mesaRepository.save(mesa);

                    return ResponseEntity
                            .noContent()
                            .<Void>build();
                })
                .orElse(
                        ResponseEntity
                                .notFound()
                                .build()
                );
    }
}