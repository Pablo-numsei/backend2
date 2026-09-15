package com.itb.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PedidoCreateRequest(

        @NotNull(message = "A mesa é obrigatória")
        Long mesaId,

        Long criadoPor,

        @NotEmpty(message = "O pedido precisa ter pelo menos um item")
        List<@Valid ItemPedidoRequest> itens

) {
}