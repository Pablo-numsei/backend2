package com.itb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "Usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @Column(name = "nome", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 180, unique = true)
    private String email;

    // Nunca serÃ¡ devolvido pela API.
    @JsonIgnore
    @Column(name = "senha_hash", nullable = false, length = 255)
    private String passwordHash;

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
