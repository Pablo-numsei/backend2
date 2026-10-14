package com.itb.dto;

import java.math.BigDecimal;
import java.util.List;

/** Staff table overview containing only the orders that keep a table occupied. */
public record MesaPanelResponse(Long id, Integer number, String status, List<OrderSummary> orders) {
    public MesaPanelResponse {
        orders = List.copyOf(orders);
    }

    public record OrderSummary(Long id, String status, BigDecimal totalValue, boolean paid) { }
}
