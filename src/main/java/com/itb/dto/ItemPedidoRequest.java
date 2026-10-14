package com.itb.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record ItemPedidoRequest(

        @NotNull(message = "O produto é obrigatório")
        @Positive
        Long produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        @Max(value = 100, message = "A quantidade máxima por produto é 100")
        Integer quantidade

) {
}
