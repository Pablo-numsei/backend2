package com.itb.dto;

import com.itb.model.ItemPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemPedidoResponse(
        Long id, ProductSummary produto, Integer quantidade,
        BigDecimal precoUnitario, BigDecimal subtotal, LocalDateTime criadoEm
) {
    public static ItemPedidoResponse from(ItemPedido item) {
        return new ItemPedidoResponse(item.getId(),
                item.getProduto() == null ? null
                        : new ProductSummary(item.getProduto().getId(), item.getProduto().getName()),
                item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal(), item.getCriadoEm());
    }

    public record ProductSummary(Long id, String name) { }
}
