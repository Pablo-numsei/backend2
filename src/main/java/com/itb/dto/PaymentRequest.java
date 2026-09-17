package com.itb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentRequest(

        @NotBlank(message = "O método de pagamento é obrigatório")
        @Pattern(
                regexp = "PIX|DEBITO|CREDITO",
                message = "O método deve ser PIX, DEBITO ou CREDITO"
        )
        String method,

        @Size(
                max = 150,
                message = "A referência do gateway deve ter no máximo 150 caracteres"
        )
        String gatewayReference

) {
}