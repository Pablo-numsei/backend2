package com.itb.dto;

/** Cliente autenticado: nenhuma permissão de funcionário e nenhum dado sensível. */
public record ClienteProfileResponse(Long id, String nomeUsuario) {}
