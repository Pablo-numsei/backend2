package com.itb.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PedidoCreateRequest(

        @NotNull(message = "A mesa é obrigatória")
        @Positive
        Long mesaId,

        Long criadoPor,

        @NotEmpty(message = "O pedido precisa ter pelo menos um item")
        @Size(max = 50, message = "O pedido aceita no máximo 50 itens distintos")
        List<@NotNull @Valid ItemPedidoRequest> itens,

        @Size(max = 80, message = "Nome do cliente muito longo.")
        String nomeCliente,

        @Size(max = 20, message = "Telefone muito longo.")
        String telefone,

        Boolean receberWhatsApp

) {
    /** Compatibilidade com integrações internas da equipe. */
    public PedidoCreateRequest(Long mesaId, Long criadoPor, List<ItemPedidoRequest> itens) {
        this(mesaId, criadoPor, itens, null, null, false);
    }
}
