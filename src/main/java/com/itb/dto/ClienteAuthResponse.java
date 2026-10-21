package com.itb.dto;

/** Customer-only session. Never contains a staff role or permissions. */
public record ClienteAuthResponse(Long id, String nomeUsuario, String email, String token) {}
