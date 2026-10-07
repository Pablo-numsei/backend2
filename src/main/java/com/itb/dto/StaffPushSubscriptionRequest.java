package com.itb.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffPushSubscriptionRequest(
        @NotBlank(message = "O endpoint é obrigatório")
        @Size(max = 1500)
        String endpoint,

        @NotNull(message = "As chaves da inscrição são obrigatórias")
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank(message = "A chave p256dh é obrigatória")
            @Size(max = 255)
            String p256dh,

            @NotBlank(message = "A chave auth é obrigatória")
            @Size(max = 255)
            String auth
    ) {
    }
}
