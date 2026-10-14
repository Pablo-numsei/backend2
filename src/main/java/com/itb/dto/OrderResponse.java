package com.itb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.itb.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id, MesaSummary mesa, StatusSummary status, BigDecimal totalValue,
        Boolean confirmed, LocalDateTime confirmedAt, LocalDateTime createdAt,
        LocalDateTime updatedAt,
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
                order.getTotalValue(), order.getConfirmed(), order.getConfirmedAt(),
                order.getCreatedAt(), order.getUpdatedAt(), accessToken);
    }

    public record MesaSummary(Long id, Integer number) { }
    public record StatusSummary(Long id, String name, Integer flowOrder) { }
}
