package com.itb.service;

import com.itb.dto.LoginRequest;
import com.itb.dto.LoginResponse;
import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {

        Usuario usuario = usuarioRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "E-mail ou senha inválidos"
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getActive())) {
            throw new IllegalStateException(
                    "Usuário inativo"
            );
        }

        boolean senhaCorreta =
                passwordEncoder.matches(
                        request.senha(),
                        usuario.getPasswordHash()
                );

        if (!senhaCorreta) {
            throw new IllegalArgumentException(
                    "E-mail ou senha inválidos"
            );
        }

        String token =
                jwtService.generateToken(usuario);

        return new LoginResponse(
                usuario.getId(),
                usuario.getName(),
                usuario.getEmail(),
                usuario.getPerfil().getId(),
                usuario.getPerfil().getName(),
                token
        );
    }
}