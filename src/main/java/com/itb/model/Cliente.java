package com.itb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** Cliente do cardápio: separado dos usuários da equipe e sem permissões administrativas. */
@Entity
@Table(name = "Clientes")
@Data
@NoArgsConstructor
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id;

    @Column(name = "nome_usuario", nullable = false, unique = true, length = 40)
    private String username;

    @Column(name = "email", nullable = true, length = 180)
    private String email;

    @JsonIgnore
    @Column(name = "senha_hash", nullable = true, length = 255)
    private String passwordHash;

    @JsonIgnore
    @Column(name = "pin_hash", nullable = true, length = 255)
    private String pinHash;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
