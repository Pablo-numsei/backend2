package com.itb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Digite um e-mail válido")
        @Size(max = 180)
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(max = 72)
        String senha

) {
}
