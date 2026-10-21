package com.itb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.itb.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;

public record OrderResponse(
        Long id, MesaSummary mesa, StatusSummary status, BigDecimal totalValue,
        String nomeCliente, String observacoes,
        Boolean confirmed, LocalDateTime confirmedAt, LocalDateTime createdAt,
        // New orders: unambiguous UTC instant. Null for records created before migration.
        Instant createdAtUtc, LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String accessToken
) {
    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, String accessToken) {
        return new OrderResponse(order.getId(),
                order.getMesa() == null ? null
                        : new MesaSummary(order.getMesa().getId(), order.getMesa().getNumber()),
                order.getStatus() == null ? null
                        : new StatusSummary(order.getStatus().getId(), order.getStatus().getName(),
                                order.getStatus().getFlowOrder()),
                order.getTotalValue(), order.getCustomerName(), order.getObservacoes(),
                order.getConfirmed(), order.getConfirmedAt(), order.getCreatedAt(),
                order.getCreatedAtUtc() == null ? null : order.getCreatedAtUtc().toInstant(ZoneOffset.UTC),
                order.getUpdatedAt(), accessToken);
    }

    public record MesaSummary(Long id, Integer number) { }
    public record StatusSummary(Long id, String name, Integer flowOrder) { }
}
