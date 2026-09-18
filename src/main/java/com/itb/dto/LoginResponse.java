package com.itb.dto;

public record LoginResponse(
        Long id,
        String nome,
        String email,
        Long perfilId,
        String perfil,
        String token
) {
}