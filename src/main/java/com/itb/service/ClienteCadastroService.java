package com.itb.service;

import com.itb.dto.ClienteCadastroRequest;
import com.itb.dto.ClienteCadastroResponse;
import com.itb.model.Cliente;
import com.itb.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteCadastroService {
    private final ClienteRepository clientes;
    private final ClientePinService pins;

    @Transactional
    public ClienteCadastroResponse cadastrar(ClienteCadastroRequest request) {
        String username = request.nomeUsuario().trim();
        ClientePinService.validate(request.pin());
        if (clientes.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Este nome de usuário já está em uso.");
        }

        Cliente cliente = new Cliente();
        cliente.setUsername(username);
        cliente.setPinHash(pins.encode(username, request.pin()));
        // No email, passwords, customer roles, or staff accounts are created.
        Cliente saved = clientes.saveAndFlush(cliente);
        return new ClienteCadastroResponse(saved.getId(), saved.getUsername(),
                "Conta criada. Escaneie o QR Code da mesa para pedir.");
    }
}
