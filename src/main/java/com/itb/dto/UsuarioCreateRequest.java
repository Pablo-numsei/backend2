package com.itb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioCreateRequest(

        @NotNull(message = "O perfil Ã© obrigatÃ³rio")
        Long perfilId,

        @NotBlank(message = "O nome Ã© obrigatÃ³rio")
        String nome,

        @NotBlank(message = "O e-mail Ã© obrigatÃ³rio")
        @Email(message = "Digite um e-mail vÃ¡lido")
        String email,

        @NotBlank(message = "A senha Ã© obrigatÃ³ria")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
        String senha

) {
}
