package com.itb.service;

import com.itb.dto.ClienteCadastroRequest;
import com.itb.dto.ClienteCadastroResponse;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import com.itb.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteCadastroServiceTest {
    @Mock private ClienteRepository clientes;
    @Mock private UsuarioRepository funcionarios;
    @Mock private PasswordEncoder encoder;
    @InjectMocks private ClienteCadastroService service;

    @Test
    void registersInSeparateTableWithHashedPasswordAndNormalizedEmail() {
        when(encoder.encode("minha-senha-forte-123")).thenReturn("bcrypt-hash");
        when(clientes.saveAndFlush(any(Cliente.class))).thenAnswer(call -> {
            Cliente cliente = call.getArgument(0);
            cliente.setId(7L);
            return cliente;
        });
        ClienteCadastroResponse response = service.cadastrar(
                new ClienteCadastroRequest("  cliente.tcc  ", "  CLIENTE@EXAMPLE.TEST  ", "minha-senha-forte-123"));
        ArgumentCaptor<Cliente> saved = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).saveAndFlush(saved.capture());
        assertEquals("cliente.tcc", saved.getValue().getUsername());
        assertEquals("cliente@example.test", saved.getValue().getEmail());
        assertEquals("bcrypt-hash", saved.getValue().getPasswordHash());
        assertEquals(7L, response.id());
        assertEquals("cliente.tcc", response.nomeUsuario());
        verify(funcionarios, never()).save(any());
    }

    @Test
    void refusesDuplicateCustomerUsernameOrExistingStaffEmail() {
        when(clientes.existsByUsernameIgnoreCase("cliente.tcc")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.cadastrar(
                new ClienteCadastroRequest("cliente.tcc", "novo@example.test", "minha-senha-forte-123")));

        reset(clientes);
        when(funcionarios.existsByEmailIgnoreCase("equipe@example.test")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.cadastrar(
                new ClienteCadastroRequest("novo", "equipe@example.test", "minha-senha-forte-123")));
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void refusesUtf8PasswordLongerThanBcryptLimit() {
        String tooLong = "á".repeat(37);
        assertTrue(tooLong.getBytes(StandardCharsets.UTF_8).length > 72);
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(
                new ClienteCadastroRequest("cliente.tcc", "novo@example.test", tooLong)));
        verifyNoInteractions(clientes, funcionarios, encoder);
    }
}
