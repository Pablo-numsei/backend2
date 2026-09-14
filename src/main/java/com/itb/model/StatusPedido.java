package com.itb.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Status_Pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_status")
    private Long id;

    @Column(name = "nome", nullable = false, length = 30, unique = true)
    private String name;

    @Column(name = "ordem_fluxo", nullable = false, unique = true)
    private Integer flowOrder;
}