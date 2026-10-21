package com.itb.service;

import com.itb.dto.ClienteAuthResponse;
import com.itb.dto.ClienteLoginRequest;
import com.itb.dto.ClienteProfileResponse;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClienteAuthService {
    private static final String MISSING_CLIENT_HASH =
            new BCryptPasswordEncoder().encode("unknown-customer-timing-check");

    private final ClienteRepository clientes;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public ClienteAuthResponse login(ClienteLoginRequest request) {
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Cliente cliente = clientes.findByEmailIgnoreCase(email).orElse(null);
        // Perform a BCrypt comparison even when no customer matches to reduce user enumeration.
        boolean matches = passwords.matches(request.senha(),
                cliente == null ? MISSING_CLIENT_HASH : cliente.getPasswordHash());
        if (!matches || cliente == null) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        return new ClienteAuthResponse(cliente.getId(), cliente.getUsername(),
                cliente.getEmail(), jwt.generateCustomerToken(cliente));
    }

    public ClienteProfileResponse profile(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw invalid();
        JwtService.CustomerTokenClaims claims =
                jwt.readCustomerToken(authorization.substring("Bearer ".length()));
        if (claims == null) throw invalid();

        Cliente cliente = clientes.findById(claims.clienteId()).orElseThrow(this::invalid);
        if (!cliente.getEmail().equalsIgnoreCase(claims.email())) throw invalid();
        return new ClienteProfileResponse(cliente.getId(), cliente.getUsername(), cliente.getEmail());
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Sessão de cliente inválida ou expirada.");
    }
}
