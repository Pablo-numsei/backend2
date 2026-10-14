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
                max = 0,
                message = "A confirmação de um gateway não pode ser enviada pelo cliente"
        )
        String gatewayReference

) {
}
