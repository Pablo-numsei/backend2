package com.itb.service;

import com.itb.dto.LoginRequest;
import com.itb.dto.LoginResponse;
import com.itb.model.Usuario;
import com.itb.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String missingUserHash = new BCryptPasswordEncoder().encode("unregistered-account-timing-check");

    public LoginResponse login(LoginRequest request) {

        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);

        boolean senhaCorreta =
                passwordEncoder.matches(
                        request.senha(),
                        usuario == null ? missingUserHash : usuario.getPasswordHash()
                );

        if (!senhaCorreta || usuario == null || !Boolean.TRUE.equals(usuario.getActive())) {
            throw new BadCredentialsException(
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
