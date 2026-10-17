package com.itb.service;

import com.itb.dto.ClienteCadastroRequest;
import com.itb.dto.ClienteCadastroResponse;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import com.itb.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClienteCadastroService {
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ClienteCadastroResponse cadastrar(ClienteCadastroRequest request) {
        String username = request.nomeUsuario().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // BCrypt aceita no máximo 72 bytes; caracteres UTF-8 podem ocupar mais de um byte.
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes em UTF-8.");
        }

        // Não reutilizar contas da equipe nem permitir que o cliente escolha um perfil.
        if (clienteRepository.existsByUsernameIgnoreCase(username)
                || clienteRepository.existsByEmailIgnoreCase(email)
                || usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Nome de usuário ou e-mail indisponível.");
        }

        Cliente cliente = new Cliente();
        cliente.setUsername(username);
        cliente.setEmail(email);
        cliente.setPasswordHash(passwordEncoder.encode(request.senha()));
        Cliente saved = clienteRepository.saveAndFlush(cliente);

        return new ClienteCadastroResponse(saved.getId(), saved.getUsername(),
                "Conta criada. Para fazer pedidos, utilize o QR Code da mesa.");
    }
}
