package com.itb.dto;

import com.itb.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String method,
        String status,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getProcessedAt()
        );
    }
}
