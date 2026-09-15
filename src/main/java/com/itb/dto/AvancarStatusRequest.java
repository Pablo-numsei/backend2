package com.itb.dto;

import jakarta.validation.constraints.NotBlank;

public record AvancarStatusRequest(

        @NotBlank(message = "O novo status é obrigatório")
        String novoStatus,

        Long alteradoPor

) {
}