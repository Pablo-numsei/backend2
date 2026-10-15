package com.itb.service;

import com.itb.dto.DesperdicioCreateRequest;
import com.itb.dto.DesperdicioResponse;
import com.itb.dto.DesperdicioResumoResponse;
import com.itb.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Service
public class DesperdicioService {

    private final JdbcTemplate jdbc;

    public DesperdicioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_HISTORICO = """
            SELECT d.id_desperdicio, d.produto_id,
                   COALESCE(p.nome, N'Produto nao localizado') AS produto_nome,
                   d.quantidade, d.motivo, d.observacao,
                   d.custo_unitario_informado AS valor_unitario,
                   CAST(d.quantidade AS DECIMAL(18,2)) * d.custo_unitario_informado AS valor_total,
                   d.estoque_antes, d.estoque_depois,
                   COALESCE(u.nome, CONCAT(N'Usuario #', d.registrado_por)) AS operador_nome,
                   d.registrado_em
              FROM dbo.Desperdicios_Produtos d
              LEFT JOIN dbo.Produtos p ON p.id_produto = d.produto_id
              LEFT JOIN dbo.Usuarios u ON u.id_usuario = d.registrado_por
            """;

    public List<DesperdicioResponse> listar() {
        return jdbc.query("""
                SELECT TOP (100) d.id_desperdicio, d.produto_id,
                       COALESCE(p.nome, N'Produto nao localizado') AS produto_nome,
                       d.quantidade, d.motivo, d.observacao,
                       d.custo_unitario_informado AS valor_unitario,
                       CAST(d.quantidade AS DECIMAL(18,2)) * d.custo_unitario_informado AS valor_total,
                       d.estoque_antes, d.estoque_depois,
                       COALESCE(u.nome, CONCAT(N'Usuario #', d.registrado_por)) AS operador_nome,
                       d.registrado_em
                  FROM dbo.Desperdicios_Produtos d
                  LEFT JOIN dbo.Produtos p ON p.id_produto = d.produto_id
                  LEFT JOIN dbo.Usuarios u ON u.id_usuario = d.registrado_por
                 ORDER BY d.registrado_em DESC, d.id_desperdicio DESC
                """, this::mapear);
    }

    // Soma os custos registrados, nao o preco de venda dos produtos.
    public DesperdicioResumoResponse resumo() {
        return jdbc.queryForObject("""
                SELECT COUNT_BIG(*) AS registros,
                       COALESCE(SUM(CAST(quantidade AS BIGINT)), 0) AS unidades,
                       COALESCE(SUM(CAST(quantidade AS DECIMAL(18,2)) * custo_unitario_informado),
                                CAST(0 AS DECIMAL(18,2))) AS prejuizo
                  FROM dbo.Desperdicios_Produtos
                 WHERE registrado_em >= DATEADD(day, -30, SYSUTCDATETIME())
                """, (rs, rowNum) -> new DesperdicioResumoResponse(
                rs.getLong("registros"), rs.getLong("unidades"), rs.getBigDecimal("prejuizo")));
    }

    @Transactional
    public DesperdicioResponse registrar(DesperdicioCreateRequest request, Long operadorId) {
        if (request == null || request.produtoId() == null || request.produtoId() <= 0 ||
                request.quantidade() == null || request.quantidade() <= 0 || request.quantidade() > 1000000 ||
                request.estoqueEsperado() == null || request.estoqueEsperado() < 0) {
            throw new IllegalArgumentException("Produto, quantidade e estoque esperado devem ser validos.");
        }
        if (operadorId == null || operadorId <= 0) {
            throw new IllegalArgumentException("Operador nao identificado ou inativo.");
        }

        BigDecimal custo = request.custoUnitarioInformado();
        if (custo == null || custo.signum() < 0 || custo.scale() > 2 || custo.precision() - custo.scale() > 10) {
            throw new IllegalArgumentException("Informe o custo unitario em reais, com ate duas casas decimais.");
        }

        String motivo = normalizarMotivo(request.motivo());
        String observacao = request.observacao() == null ? null : request.observacao().trim();
        if (observacao != null && observacao.length() > 255) {
            throw new IllegalArgumentException("A observacao deve ter ate 255 caracteres.");
        }
        if (observacao != null && observacao.isEmpty()) observacao = null;

        // Bloqueio consistente com os demais ajustes de estoque do projeto.
        List<ProdutoAtual> produtos = jdbc.query("""
                SELECT id_produto, estoque
                  FROM dbo.Produtos WITH (UPDLOCK, HOLDLOCK)
                 WHERE id_produto = ? AND ativo = 1
                """, (rs, rowNum) -> new ProdutoAtual(rs.getLong("id_produto"), rs.getInt("estoque")),
                request.produtoId());

        if (produtos.isEmpty()) throw new ResourceNotFoundException("Produto nao encontrado ou inativo.");
        ProdutoAtual produto = produtos.getFirst();

        if (produto.estoque() != request.estoqueEsperado()) {
            throw new IllegalStateException("O estoque mudou. Atualize a tela antes de registrar a perda.");
        }
        if (request.quantidade() > produto.estoque()) {
            throw new IllegalArgumentException("O desperdicio nao pode exceder o estoque disponivel.");
        }

        int estoqueDepois = produto.estoque() - request.quantidade();
        int linhas = jdbc.update("""
                UPDATE dbo.Produtos
                   SET estoque = ?, alterado_em = GETDATE()
                 WHERE id_produto = ? AND ativo = 1 AND estoque = ?
                """, estoqueDepois, produto.id(), produto.estoque());
        if (linhas != 1) throw new IllegalStateException("O estoque mudou. Atualize a tela e tente novamente.");

        // Registrado_por e BIGINT no banco existente; recebe o id de dbo.Usuarios,
        // nunca o email do token. Falhas no INSERT desfazem o desconto (transacao).
        Long id = jdbc.queryForObject("""
                INSERT INTO dbo.Desperdicios_Produtos
                    (produto_id, quantidade, motivo, observacao,
                     custo_unitario_informado, registrado_em, registrado_por,
                     estoque_antes, estoque_depois)
                OUTPUT INSERTED.id_desperdicio
                VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), ?, ?, ?)
                """, Long.class, produto.id(), request.quantidade(), motivo,
                observacao, custo, operadorId, produto.estoque(), estoqueDepois);

        return jdbc.queryForObject(SELECT_HISTORICO + """
                 WHERE d.id_desperdicio = ?
                """, this::mapear, id);
    }

    private String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Informe o motivo do desperdicio.");
        }
        String normalizado = motivo.trim().toUpperCase(Locale.ROOT);
        if (!List.of("VENCIMENTO", "PREPARO", "DANIFICADO", "OUTRO").contains(normalizado)) {
            throw new IllegalArgumentException("Motivo invalido. Use VENCIMENTO, PREPARO, DANIFICADO ou OUTRO.");
        }
        return normalizado;
    }

    private DesperdicioResponse mapear(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal unitario = rs.getBigDecimal("valor_unitario");
        BigDecimal total = rs.getBigDecimal("valor_total");
        return new DesperdicioResponse(
                rs.getLong("id_desperdicio"), rs.getLong("produto_id"),
                rs.getString("produto_nome"), rs.getInt("quantidade"),
                rs.getString("motivo"), rs.getString("observacao"),
                unitario, total, rs.getInt("estoque_antes"), rs.getInt("estoque_depois"),
                rs.getString("operador_nome"), rs.getTimestamp("registrado_em").toLocalDateTime());
    }

    private record ProdutoAtual(Long id, int estoque) {}
}
