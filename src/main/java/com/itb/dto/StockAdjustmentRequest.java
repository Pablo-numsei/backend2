package com.itb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockAdjustmentRequest(
        @NotNull(message = "A quantidade do ajuste é obrigatória")
        @Min(value = -1000000, message = "O ajuste de estoque excede o limite permitido")
        @Max(value = 1000000, message = "O ajuste de estoque excede o limite permitido")
        Integer delta,
        @NotNull(message = "O estoque exibido antes do ajuste é obrigatório")
        @PositiveOrZero(message = "O estoque esperado não pode ser negativo")
        Integer expectedStockQuantity
) {}
