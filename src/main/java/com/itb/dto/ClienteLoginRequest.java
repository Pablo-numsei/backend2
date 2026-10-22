package com.itb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteLoginRequest(
    @NotBlank @Size(min=3,max=40) String nomeUsuario,
    @NotBlank @Pattern(regexp = "^[0-9]{4}$") String pin
) {}
