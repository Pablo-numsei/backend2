package com.itb.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Itens_Pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item")
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Order pedido;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Product produto;

    @NotNull
    @Positive
    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;

    @NotNull
    @Column(
        name = "preco_unitario",
        nullable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal precoUnitario;

    @Column(
        name = "subtotal",
        insertable = false,
        updatable = false,
        precision = 10,
        scale = 2
    )
    private BigDecimal subtotal;

    @Column(
        name = "criado_em",
        insertable = false,
        updatable = false
    )
    private LocalDateTime criadoEm;
}