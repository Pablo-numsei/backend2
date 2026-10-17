package com.itb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteCadastroRequest(
        @NotBlank(message = "Informe um nome de usuário.")
        @Size(min = 3, max = 40, message = "O nome de usuário deve ter de 3 a 40 caracteres.")
        @Pattern(regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N}_.-]*$",
                 message = "Use letras, números, ponto, hífen ou sublinhado no nome de usuário.")
        String nomeUsuario,

        @NotBlank(message = "Informe seu e-mail.")
        @Email(message = "Digite um e-mail válido.")
        @Size(max = 180, message = "O e-mail é muito longo.")
        String email,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 12, max = 72, message = "Use uma senha de 12 a 72 caracteres.")
        String senha
) {}
