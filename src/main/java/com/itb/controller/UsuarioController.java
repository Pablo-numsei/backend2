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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    // =========================
    // LISTAR USUÁRIOS
    // =========================
    @GetMapping
    public ResponseEntity<List<Usuario>> getAll() {

        return ResponseEntity.ok(
                usuarioRepository.findAll()
        );
    }

    // =========================
    // BUSCAR USUÁRIO POR ID
    // =========================
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getById(
            @PathVariable Long id
    ) {

        return usuarioRepository
                .findById(id)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // CRIAR USUÁRIO
    // =========================
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(
            @Valid @RequestBody UsuarioCreateRequest request
    ) {

        if (usuarioRepository.existsByEmailIgnoreCase(
                request.email()
        )) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            "Já existe um usuário com esse e-mail."
                    );
        }

        Perfil perfil = perfilRepository
                .findById(request.perfilId())
                .orElse(null);

        if (perfil == null) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            "Perfil informado não existe."
                    );
        }

        Usuario usuario = new Usuario();

        usuario.setPerfil(perfil);
        usuario.setName(request.nome());
        usuario.setEmail(request.email());

        usuario.setPasswordHash(
                passwordEncoder.encode(
                        request.senha()
                )
        );

        Usuario saved =
                usuarioRepository.saveAndFlush(usuario);

        entityManager.refresh(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }
}