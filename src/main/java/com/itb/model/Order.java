package com.itb.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false)
    private StatusPedido status;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "confirmado", nullable = false)
    private Boolean confirmed;

    @Column(name = "confirmado_em")
    private LocalDateTime confirmedAt;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "alterado_em")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "alterado_por")
    private Usuario updatedBy;

    @Column(name = "excluido_em")
    private LocalDateTime deletedAt;
}