package com.itb.dto;

import jakarta.validation.constraints.NotBlank;

public record AtendimentoStatusRequest(
        @NotBlank String status
) {
}
