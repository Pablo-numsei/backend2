package com.itb.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "Perfis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_perfil")
    private Long id;

    @Column(name = "nome", nullable = false, length = 30, unique = true)
    private String name;

    @Column(name = "descricao", length = 200)
    private String description;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
