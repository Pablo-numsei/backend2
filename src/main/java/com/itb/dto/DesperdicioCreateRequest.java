package com.itb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DesperdicioCreateRequest(
        @NotNull @Positive Long produtoId,
        @NotNull @Positive @Max(1000000) Integer quantidade,
        @NotNull @PositiveOrZero Integer estoqueEsperado,
        @NotBlank @Size(max = 20) String motivo,
        @Size(max = 255) String observacao
) {}
