package com.itb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteCadastroRequest(
    @NotBlank(message = "Informe um nome de usuário.")
    @Size(min = 3, max = 40, message = "Use de 3 a 40 caracteres no nome de usuário.")
    @Pattern(regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N}_.-]*$",
             message = "Use letras, números, ponto, hífen ou sublinhado.")
    String nomeUsuario,

    @NotBlank(message = "Escolha um PIN de 4 dígitos.")
    @Pattern(regexp = "^[0-9]{4}$", message = "O PIN deve ter exatamente 4 números.")
    String pin
) {}
