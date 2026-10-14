package com.itb.service;

import com.itb.dto.AtendimentoCreateRequest;
import com.itb.dto.AtendimentoResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AtendimentoService {

    private final JdbcTemplate jdbcTemplate;
    private final PushNotificationService pushNotificationService;

    public AtendimentoService(JdbcTemplate jdbcTemplate, PushNotificationService pushNotificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.pushNotificationService = pushNotificationService;
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
                """, this::mapAtendimento);
    }

    @Transactional
    public AtendimentoResponse criar(AtendimentoCreateRequest request) {
        String tipo = normalizarTipo(request.tipo());
        String detalhe = normalizarDetalhe(request.detalhe());

        // A linha existente da mesa serializa a busca e a inserção entre instâncias.
        // Nenhuma chamada de rede acontece enquanto esse lock está retido.
        try {
            Long mesa = jdbcTemplate.queryForObject(
                    "SELECT id_mesa FROM dbo.Mesas WITH (UPDLOCK, HOLDLOCK) WHERE id_mesa = ? AND ativa = 1",
                    Long.class, request.mesaId());
            if (mesa == null) throw new IllegalArgumentException("Mesa não encontrada ou inativa.");
        } catch (EmptyResultDataAccessException missingMesa) {
            throw new IllegalArgumentException("Mesa não encontrada ou inativa.");
        }

        if (request.pedidoId() != null) {
            Integer pedidoExiste = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.Pedidos WHERE id_pedido = ? AND mesa_id = ? AND excluido_em IS NULL",
                    Integer.class, request.pedidoId(), request.mesaId());
            if (pedidoExiste == null || pedidoExiste == 0) {
                throw new IllegalArgumentException("Pedido não encontrado para esta mesa.");
            }
        }

        AtendimentoResponse existente = buscarAbertoIdentico(request.mesaId(), request.pedidoId(), tipo, detalhe);
        if (existente != null) return existente;

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.Atendimentos_Mesa
                    (mesa_id, pedido_id, tipo, detalhe, status, criado_em, atualizado_em)
                OUTPUT INSERTED.id_atendimento
                VALUES (?, ?, ?, ?, 'ENVIADO', SYSUTCDATETIME(), SYSUTCDATETIME())
                """, Long.class, request.mesaId(), request.pedidoId(), tipo, detalhe);

        AtendimentoResponse atendimento = buscarPorId(id);
        // O dispatcher de push registra o envio após commit e só é chamado para novas linhas.
        pushNotificationService.notifyStaffAtendimento(atendimento);
        return atendimento;
    }

    @Transactional
    public AtendimentoResponse atualizarStatus(Long id, String novoStatus) {
        String status = normalizarStatus(novoStatus);
        AtendimentoResponse atual = buscarPorId(id);
        if (status.equals(atual.status())) return atual;

        String esperado = switch (atual.status()) {
            case "ENVIADO" -> "EM_ATENDIMENTO";
            case "EM_ATENDIMENTO" -> "CONCLUIDO";
            case "CONCLUIDO" -> throw new IllegalArgumentException("O atendimento já foi concluído.");
            default -> throw new IllegalStateException("Status atual inválido.");
        };
        if (!status.equals(esperado)) {
            throw new IllegalArgumentException("Transição inválida. Próximo status permitido: " + esperado);
        }

        int updated = jdbcTemplate.update("""
                UPDATE dbo.Atendimentos_Mesa
                   SET status = ?,
                       atualizado_em = SYSUTCDATETIME()
                 WHERE id_atendimento = ? AND status = ?
                """, status, id, atual.status());
        if (updated == 0) {
            AtendimentoResponse changed = buscarPorId(id);
            if (status.equals(changed.status())) return changed;
            throw new IllegalStateException("O atendimento mudou. Atualize a lista e tente novamente.");
        }
        return buscarPorId(id);
    }

    private AtendimentoResponse buscarAbertoIdentico(Long mesaId, Long pedidoId, String tipo, String detalhe) {
        List<Object> parametros = new ArrayList<>();
        parametros.add(mesaId);
        parametros.add(tipo);
        String pedidoFiltro = pedidoId == null ? "a.pedido_id IS NULL" : "a.pedido_id = ?";
        if (pedidoId != null) parametros.add(pedidoId);
        String detalheFiltro = detalhe == null
                ? "NULLIF(LTRIM(RTRIM(a.detalhe)), N'') IS NULL"
                : "LTRIM(RTRIM(a.detalhe)) COLLATE Latin1_General_100_BIN2 = ?";
        if (detalhe != null) parametros.add(detalhe);

        // Somente os predicados constantes variam; todos os valores permanecem parametrizados.
        String sql = """
                SELECT TOP (1) a.id_atendimento,
                       a.mesa_id, m.numero AS mesa_numero, a.pedido_id,
                       a.tipo, a.detalhe, a.status, a.criado_em, a.atualizado_em
                  FROM dbo.Atendimentos_Mesa a
                  INNER JOIN dbo.Mesas m ON m.id_mesa = a.mesa_id
                 WHERE a.mesa_id = ? AND a.tipo = ?
                   AND %s AND %s
                   AND a.status IN ('ENVIADO', 'EM_ATENDIMENTO')
                 ORDER BY a.criado_em DESC, a.id_atendimento DESC
                """.formatted(pedidoFiltro, detalheFiltro);
        List<AtendimentoResponse> abertos = jdbcTemplate.query(sql, this::mapAtendimento, parametros.toArray());
        return abertos.isEmpty() ? null : abertos.getFirst();
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
                """, this::mapAtendimento, id);
    }

    private AtendimentoResponse mapAtendimento(ResultSet rs, int rowNum) throws SQLException {
        return new AtendimentoResponse(
                rs.getLong("id_atendimento"), rs.getLong("mesa_id"), rs.getInt("mesa_numero"),
                rs.getObject("pedido_id", Long.class), rs.getString("tipo"), rs.getString("detalhe"),
                rs.getString("status"), rs.getTimestamp("criado_em").toLocalDateTime(),
                rs.getTimestamp("atualizado_em").toLocalDateTime());
    }

    private String normalizarTipo(String tipo) {
        String value = tipo.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("GARCOM") && !value.equals("CONTA")) {
            throw new IllegalArgumentException("Tipo deve ser GARCOM ou CONTA.");
        }
        return value;
    }

    private String normalizarDetalhe(String detalhe) {
        if (detalhe == null) return null;
        String value = detalhe.trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizarStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("ENVIADO") && !value.equals("EM_ATENDIMENTO") && !value.equals("CONCLUIDO")) {
            throw new IllegalArgumentException("Status de atendimento inválido.");
        }
        return value;
    }
}
