package com.itb.service;

import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AtendimentoService {

    private final JdbcTemplate jdbcTemplate;

    public AtendimentoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AtendimentoResponse> listar() {
        return jdbcTemplate.query("""
                SELECT a.id_atendimento,
                       a.mesa_id,
                       m.numero AS mesa_numero,
                       a.pedido_id,
                       a.tipo,
                       a.detalhe,
                       a.status,
                       a.criado_em,
                       a.atualizado_em
                  FROM dbo.Atendimentos_Mesa a
                  INNER JOIN dbo.Mesas m ON m.id_mesa = a.mesa_id
                 ORDER BY a.criado_em DESC, a.id_atendimento DESC
                """, (rs, rowNum) -> new AtendimentoResponse(
                rs.getLong("id_atendimento"),
                rs.getLong("mesa_id"),
                rs.getInt("mesa_numero"),
                rs.getObject("pedido_id", Long.class),
                rs.getString("tipo"),
                rs.getString("detalhe"),
                rs.getString("status"),
                rs.getTimestamp("criado_em").toLocalDateTime(),
                rs.getTimestamp("atualizado_em").toLocalDateTime()
        ));
    }

    @Transactional
    public AtendimentoResponse criar(AtendimentoCreateRequest request) {
        String tipo = normalizarTipo(request.tipo());

        Integer mesaExiste = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.Mesas WHERE id_mesa = ? AND ativo = 1",
                Integer.class,
                request.mesaId()
        );

        if (mesaExiste == null || mesaExiste == 0) {
            throw new IllegalArgumentException("Mesa não encontrada ou inativa.");
        }

        if (request.pedidoId() != null) {
            Integer pedidoExiste = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.Pedidos WHERE id_pedido = ? AND mesa_id = ? AND deletado_em IS NULL",
                    Integer.class,
                    request.pedidoId(),
                    request.mesaId()
            );

            if (pedidoExiste == null || pedidoExiste == 0) {
                throw new IllegalArgumentException("Pedido não encontrado para esta mesa.");
            }
        }

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.Atendimentos_Mesa
                    (mesa_id, pedido_id, tipo, detalhe, status, criado_em, atualizado_em)
                OUTPUT INSERTED.id_atendimento
                VALUES (?, ?, ?, ?, 'ENVIADO', SYSUTCDATETIME(), SYSUTCDATETIME())
                """,
                Long.class,
                request.mesaId(),
                request.pedidoId(),
                tipo,
                request.detalhe()
        );

        return buscarPorId(id);
    }

    @Transactional
    public AtendimentoResponse atualizarStatus(Long id, String novoStatus) {
        String status = normalizarStatus(novoStatus);
        AtendimentoResponse atual = buscarPorId(id);

        String esperado = switch (atual.status()) {
            case "ENVIADO" -> "EM_ATENDIMENTO";
            case "EM_ATENDIMENTO" -> "CONCLUIDO";
            case "CONCLUIDO" -> "CONCLUIDO";
            default -> throw new IllegalStateException("Status atual inválido.");
        };

        if (!status.equals(esperado)) {
            throw new IllegalArgumentException(
                    "Transição inválida. Próximo status permitido: " + esperado
            );
        }

        jdbcTemplate.update("""
                UPDATE dbo.Atendimentos_Mesa
                   SET status = ?,
                       atualizado_em = SYSUTCDATETIME()
                 WHERE id_atendimento = ?
                """, status, id);

        return buscarPorId(id);
    }

    private AtendimentoResponse buscarPorId(Long id) {
        return jdbcTemplate.queryForObject("""
                SELECT a.id_atendimento,
                       a.mesa_id,
                       m.numero AS mesa_numero,
                       a.pedido_id,
                       a.tipo,
                       a.detalhe,
                       a.status,
                       a.criado_em,
                       a.atualizado_em
                  FROM dbo.Atendimentos_Mesa a
                  INNER JOIN dbo.Mesas m ON m.id_mesa = a.mesa_id
                 WHERE a.id_atendimento = ?
                """, (rs, rowNum) -> new AtendimentoResponse(
                rs.getLong("id_atendimento"),
                rs.getLong("mesa_id"),
                rs.getInt("mesa_numero"),
                rs.getObject("pedido_id", Long.class),
                rs.getString("tipo"),
                rs.getString("detalhe"),
                rs.getString("status"),
                rs.getTimestamp("criado_em").toLocalDateTime(),
                rs.getTimestamp("atualizado_em").toLocalDateTime()
        ), id);
    }

    private String normalizarTipo(String tipo) {
        String value = tipo.trim().toUpperCase();
        if (!value.equals("GARCOM") && !value.equals("CONTA")) {
            throw new IllegalArgumentException("Tipo deve ser GARCOM ou CONTA.");
        }
        return value;
    }

    private String normalizarStatus(String status) {
        String value = status.trim().toUpperCase();
        if (!value.equals("ENVIADO")
                && !value.equals("EM_ATENDIMENTO")
                && !value.equals("CONCLUIDO")) {
            throw new IllegalArgumentException("Status de atendimento inválido.");
        }
        return value;
    }
}
