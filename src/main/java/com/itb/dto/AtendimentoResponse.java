package com.itb.dto;

import java.time.LocalDateTime;

public record AtendimentoResponse(
        Long id,
        Long mesaId,
        Integer mesa,
        Long pedidoId,
        String tipo,
        String detalhe,
        String status,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
}
