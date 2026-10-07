package com.itb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AtendimentoCreateRequest(
        @NotNull Long mesaId,
        Long pedidoId,
        @NotBlank String tipo,
        String detalhe
) {
}
