package com.itb.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Produtos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_produto")
    private Long id;

    @NotNull(message = "A categoria Ã© obrigatÃ³ria")
    @ManyToOne
    @JoinColumn(name = "categoria_id", nullable = false)
    private Category category;

    @NotBlank(message = "O nome do produto Ã© obrigatÃ³rio")
    @Column(name = "nome", nullable = false, length = 150)
    private String name;

    @Column(name = "descricao", length = 500)
    private String description;

    @NotNull(message = "O preÃ§o Ã© obrigatÃ³rio")
    @Positive(message = "O preÃ§o deve ser positivo")
    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull(message = "O estoque Ã© obrigatÃ³rio")
    @PositiveOrZero(message = "O estoque nÃ£o pode ser negativo")
    @Column(name = "estoque", nullable = false)
    private Integer stockQuantity;

    @Column(name = "disponivel", insertable = false, updatable = false)
    private Boolean available;

    @Column(name = "ativo", insertable = false)
    private Boolean active;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "alterado_em")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
