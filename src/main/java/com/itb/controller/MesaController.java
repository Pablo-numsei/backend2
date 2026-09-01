package com.itb.controller;

import com.itb.model.Mesa;
import com.itb.repository.MesaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {

    private final MesaRepository mesaRepository;
    private final EntityManager entityManager;


    // GET /api/mesas
    @GetMapping
    public ResponseEntity<List<Mesa>> getAll() {
        return ResponseEntity.ok(mesaRepository.findAll());
    }

    // GET /api/mesas/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Mesa> getById(@PathVariable Long id) {
        return mesaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/mesas
   @PostMapping
@Transactional
public ResponseEntity<?> create(@Valid @RequestBody Mesa mesa) {
        if (mesaRepository.existsByNumber(mesa.getNumber())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("JÃ¡ existe uma mesa com esse nÃºmero.");
        }

        if (mesaRepository.existsByQrCode(mesa.getQrCode())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Esse QR Code jÃ¡ estÃ¡ sendo utilizado.");
        }

       Mesa saved = mesaRepository.saveAndFlush(mesa);

entityManager.refresh(saved);

return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(saved);
    }

    // PUT /api/mesas/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Mesa> update(
            @PathVariable Long id,
            @Valid @RequestBody Mesa mesa) {

        return mesaRepository.findById(id)
                .map(existing -> {
                    existing.setNumber(mesa.getNumber());
                    existing.setQrCode(mesa.getQrCode());

                    Mesa saved = mesaRepository.save(existing);

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE lÃ³gico: desativa a mesa
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {

        return mesaRepository.findById(id)
                .map(mesa -> {
                    mesa.setActive(false);
                    mesaRepository.save(mesa);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
