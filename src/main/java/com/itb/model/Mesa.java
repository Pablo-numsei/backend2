package com.itb.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "Mesas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mesa")
    private Long id;

    @NotNull(message = "O nÃºmero da mesa Ã© obrigatÃ³rio")
    @Positive(message = "O nÃºmero da mesa deve ser maior que zero")
    @Column(name = "numero", nullable = false, unique = true)
    private Integer number;

    @NotBlank(message = "O QR Code Ã© obrigatÃ³rio")
    @Column(name = "qr_code", nullable = false, length = 255, unique = true)
    private String qrCode;

    @Column(name = "ativa", insertable = false)
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
