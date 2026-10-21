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
    @Mock private ClientePinService pins;
    @Mock private JwtService jwt;
    @InjectMocks private ClienteAuthService service;

    private Cliente customer() {
        Cliente c = new Cliente();
        c.setId(27L);
        c.setUsername("pablo123");
        c.setPinHash("slow-hash");
        return c;
    }

    @Test
    void loginChecksUsernameAndIssuesCustomerOnlyToken() {
        Cliente c = customer();
        when(clientes.findByUsernameIgnoreCase("pablo123")).thenReturn(Optional.of(c));
        when(pins.matches("pablo123", "0472", "slow-hash")).thenReturn(true);
        when(jwt.generateCustomerToken(c)).thenReturn("signed.customer.token");
        var response = service.login(new ClienteLoginRequest(" PABLO123 ", "0472"));
        assertEquals("pablo123", response.nomeUsuario());
        assertEquals("signed.customer.token", response.token());
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void invalidPinNeverIssuesTokenAndLocksAfterFiveAttempts() {
        Cliente c = customer();
        when(clientes.findByUsernameIgnoreCase("pablo123")).thenReturn(Optional.of(c));
        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> service.login(new ClienteLoginRequest("pablo123", "0000")));
        }
        assertThrows(ResponseStatusException.class,
                () -> service.login(new ClienteLoginRequest("pablo123", "0000")));
        verify(jwt, never()).generateCustomerToken(any());
    }

    @Test
    void onlySignedExistingAndMatchedCustomerCanPlaceOrder() {
        assertThrows(ResponseStatusException.class, () -> service.requireCustomer("staff.jwt.token"));
        when(jwt.readCustomerToken("signed.customer.token"))
                .thenReturn(new JwtService.CustomerTokenClaims(27L, "pablo123"));
        when(clientes.findById(27L)).thenReturn(Optional.of(customer()));
        assertEquals(27L, service.requireCustomer("signed.customer.token").getId());
    }

    @Test
    void customerTokenWithOtherUsernameIsRejected() {
        when(jwt.readCustomerToken("signed.customer.token"))
                .thenReturn(new JwtService.CustomerTokenClaims(27L, "impostor"));
        when(clientes.findById(27L)).thenReturn(Optional.of(customer()));
        assertThrows(ResponseStatusException.class,
                () -> service.requireCustomer("signed.customer.token"));
    }
}
