package com.itb.service;

import com.itb.dto.ClienteLoginRequest;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteAuthServiceTest {
    @Mock private ClienteRepository clientes;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Mock private JwtService jwt;
    @InjectMocks private ClienteAuthService service;

    private Cliente customer() {
        Cliente cliente = new Cliente();
        cliente.setId(27L);
        cliente.setEmail("cliente@example.test");
        cliente.setUsername("cliente.tcc");
        cliente.setPasswordHash("hash-bcrypt");
        return cliente;
    }

    @Test
    void loginReturnsOnlyCustomerIdentityAndScopedToken() {
        Cliente cliente = customer();
        when(clientes.findByEmailIgnoreCase("cliente@example.test")).thenReturn(Optional.of(cliente));
        when(passwords.matches("minha-senha-forte-123", cliente.getPasswordHash())).thenReturn(true);
        when(jwt.generateCustomerToken(cliente)).thenReturn("customer.jwt.token");

        var response = service.login(new ClienteLoginRequest(" CLIENTE@EXAMPLE.TEST ", "minha-senha-forte-123"));

        assertEquals(27L, response.id());
        assertEquals("cliente.tcc", response.nomeUsuario());
        assertEquals("customer.jwt.token", response.token());
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void invalidPasswordDoesNotIssueCustomerOrStaffToken() {
        Cliente cliente = customer();
        when(clientes.findByEmailIgnoreCase("cliente@example.test")).thenReturn(Optional.of(cliente));
        when(passwords.matches("senha-errada-123", cliente.getPasswordHash())).thenReturn(false);
        assertThrows(BadCredentialsException.class,
                () -> service.login(new ClienteLoginRequest("cliente@example.test", "senha-errada-123")));
        verify(jwt, never()).generateCustomerToken(any());
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void rejectsPasswordLongerThanBcryptUtf8Limit() {
        assertThrows(BadCredentialsException.class,
                () -> service.login(new ClienteLoginRequest("cliente@example.test", "á".repeat(37))));
        verifyNoInteractions(clientes, passwords, jwt);
    }

    @Test
    void meRequiresCustomerPurposeAndMatchingActiveIdentity() {
        assertThrows(ResponseStatusException.class, () -> service.profile("Bearer staff.jwt.token"));
        when(jwt.readCustomerToken("customer.jwt.token"))
                .thenReturn(new JwtService.CustomerTokenClaims(27L, "cliente@example.test"));
        when(clientes.findById(27L)).thenReturn(Optional.of(customer()));
        var profile = service.profile("Bearer customer.jwt.token");
        assertEquals(27L, profile.id());
        assertEquals("cliente.tcc", profile.nomeUsuario());
    }
}
