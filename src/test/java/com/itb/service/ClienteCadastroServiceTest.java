package com.itb.service;

import com.itb.dto.ClienteCadastroRequest;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteCadastroServiceTest {
    @Mock private ClienteRepository clientes;
    @Mock private ClientePinService pins;
    @InjectMocks private ClienteCadastroService service;

    @Test
    void registersOnlyUsernameAndHashedPinWithoutEmailOrStaffProfile() {
        when(pins.encode("pablo123", "0472")).thenReturn("protected-pin-hash");
        when(clientes.saveAndFlush(any(Cliente.class))).thenAnswer(call -> {
            Cliente cliente = call.getArgument(0);
            cliente.setId(7L);
            return cliente;
        });
        var result = service.cadastrar(new ClienteCadastroRequest(" pablo123 ", "0472"));
        ArgumentCaptor<Cliente> capture = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).saveAndFlush(capture.capture());
        assertEquals("pablo123", capture.getValue().getUsername());
        assertEquals("protected-pin-hash", capture.getValue().getPinHash());
        assertNull(capture.getValue().getEmail());
        assertNull(capture.getValue().getPasswordHash());
        assertEquals(7L, result.id());
    }

    @Test
    void duplicateUsernameDoesNotCreateAccount() {
        when(clientes.existsByUsernameIgnoreCase("pablo123")).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> service.cadastrar(new ClienteCadastroRequest("pablo123", "0472")));
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void pinMustBeExactlyFourDigitsIncludingLeadingZero() {
        for (String invalid : new String[]{"472", "04720", "ab12", "12-3", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.cadastrar(new ClienteCadastroRequest("pablo123", invalid)));
        }
        verifyNoInteractions(clientes, pins);
    }
}
