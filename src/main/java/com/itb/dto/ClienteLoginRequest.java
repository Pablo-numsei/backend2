package com.itb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteLoginRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 72) String senha
) {}
