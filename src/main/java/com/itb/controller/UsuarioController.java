package com.itb.controller;

import com.itb.dto.UsuarioCreateRequest;
import com.itb.model.Perfil;
import com.itb.model.Usuario;
import com.itb.repository.PerfilRepository;
import com.itb.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final EntityManager entityManager;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    // GET /api/usuarios
    @GetMapping
    public ResponseEntity<List<Usuario>> getAll() {
        return ResponseEntity.ok(usuarioRepository.findAll());
    }

    // GET /api/usuarios/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getById(@PathVariable Long id) {

        return usuarioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/usuarios
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(
            @Valid @RequestBody UsuarioCreateRequest request) {

        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("JÃ¡ existe um usuÃ¡rio com esse e-mail.");
        }

        Perfil perfil = perfilRepository.findById(request.perfilId())
                .orElse(null);

        if (perfil == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Perfil informado nÃ£o existe.");
        }

        Usuario usuario = new Usuario();

        usuario.setPerfil(perfil);
        usuario.setName(request.nome());
        usuario.setEmail(request.email());

        // RN012 - senha nunca armazenada em texto puro
        usuario.setPasswordHash(
                passwordEncoder.encode(request.senha())
        );

        Usuario saved = usuarioRepository.saveAndFlush(usuario);

        entityManager.refresh(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }
}
