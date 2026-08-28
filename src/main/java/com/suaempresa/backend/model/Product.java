package com.suaempresa.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Equivalente ao antigo models/Product.js
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do produto e obrigatorio")
    private String name;

    private String description;

    @NotNull(message = "O preco e obrigatorio")
    @Positive(message = "O preco deve ser positivo")
    private BigDecimal price;

    @NotNull(message = "A quantidade em estoque e obrigatoria")
    private Integer stockQuantity;
}
