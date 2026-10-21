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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ClienteAuthService {
    private static final String MISSING_CLIENT_HASH =
            new BCryptPasswordEncoder().encode("constant-time-check-for-unknown-customer");
    private static final long LOCK_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_FAILURES = 5;

    private final ClienteRepository clientes;
    private final ClientePinService pins;
    private final JwtService jwt;
    // Per-user throttling complements existing per-IP filter. Use shared Redis for multi-instance production.
    private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();

    public ClienteAuthResponse login(ClienteLoginRequest request) {
        String username = request.nomeUsuario().trim().toLowerCase(Locale.ROOT);
        ClientePinService.validate(request.pin());
        long now = System.currentTimeMillis();
        Failures current = failures.get(username);
        if (current != null && current.lockUntil > now)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de PIN. Tente novamente mais tarde.");

        Cliente cliente = clientes.findByUsernameIgnoreCase(username).orElse(null);
        boolean matches = pins.matches(cliente == null ? username : cliente.getUsername(), request.pin(),
                cliente == null ? MISSING_CLIENT_HASH : cliente.getPinHash());

        if (!matches || cliente == null) {
            failures.compute(username, (key, previous) -> {
                int count = previous == null || (previous.lockUntil > 0 && previous.lockUntil <= now)
                        ? 1 : previous.count + 1;
                return new Failures(count, count >= MAX_FAILURES ? now + LOCK_MILLIS : 0L);
            });
            throw new BadCredentialsException("Usuário ou PIN inválidos.");
        }

        failures.remove(username);
        return new ClienteAuthResponse(cliente.getId(), cliente.getUsername(),
                jwt.generateCustomerToken(cliente));
    }

    /** Signed customer-specific token; staff/order/table tokens are rejected. */
    public Cliente requireCustomer(String token) {
        JwtService.CustomerTokenClaims claims = jwt.readCustomerToken(token);
        if (claims == null) throw invalid();
        Cliente cliente = clientes.findById(claims.clienteId()).orElseThrow(this::invalid);
        if (cliente.getPinHash() == null
                || !cliente.getUsername().equalsIgnoreCase(claims.username()))
            throw invalid();
        return cliente;
    }

    public ClienteProfileResponse profile(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw invalid();
        Cliente cliente = requireCustomer(authorization.substring("Bearer ".length()));
        return new ClienteProfileResponse(cliente.getId(), cliente.getUsername());
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão de cliente inválida ou expirada.");
    }

    private record Failures(int count, long lockUntil) {}
}
