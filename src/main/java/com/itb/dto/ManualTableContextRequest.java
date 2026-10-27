package com.itb.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ManualTableContextRequest(
        @NotNull(message = "Informe o número da mesa.")
        @Positive(message = "O número da mesa deve ser maior que zero.")
        Integer numeroMesa) {}
