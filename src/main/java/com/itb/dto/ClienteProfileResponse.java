package com.itb.dto;

/** Minimal customer profile returned only for a valid customer JWT. */
public record ClienteProfileResponse(Long id, String nomeUsuario, String nomeUsuarioExibido) {}
