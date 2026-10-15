package com.itb.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DesperdicioResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        Integer quantidade,
        String motivo,
        String observacao,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        Integer estoqueAntes,
        Integer estoqueDepois,
        String registradoPor,
        LocalDateTime registradoEm
) {}
