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

    // Historico limitado aos cem registros mais recentes.
    public List<DesperdicioResponse> listar() {
        return jdbc.query("""
                SELECT TOP (100) id_desperdicio, produto_id, produto_nome, quantidade,
                       motivo, observacao, valor_unitario, valor_total,
                       estoque_antes, estoque_depois, registrado_por, registrado_em
                  FROM dbo.Desperdicios_Produtos
                 ORDER BY registrado_em DESC, id_desperdicio DESC
                """, this::mapear);
    }

    // Indicadores de perdas nos ultimos trinta dias (horario UTC).
    public DesperdicioResumoResponse resumo() {
        return jdbc.queryForObject("""
                SELECT COUNT_BIG(*) AS registros,
                       COALESCE(SUM(CAST(quantidade AS BIGINT)), 0) AS unidades,
                       COALESCE(SUM(valor_total), CAST(0 AS DECIMAL(18,2))) AS prejuizo
                  FROM dbo.Desperdicios_Produtos
                 WHERE registrado_em >= DATEADD(day, -30, SYSUTCDATETIME())
                """, (rs, rowNum) -> new DesperdicioResumoResponse(
                rs.getLong("registros"), rs.getLong("unidades"), rs.getBigDecimal("prejuizo")));
    }

    @Transactional
    public DesperdicioResponse registrar(DesperdicioCreateRequest request, String operador) {
        if (request.produtoId() == null || request.produtoId() <= 0 ||
                request.quantidade() == null || request.quantidade() <= 0 || request.quantidade() > 1000000 ||
                request.estoqueEsperado() == null || request.estoqueEsperado() < 0) {
            throw new IllegalArgumentException("Produto, quantidade e estoque esperado devem ser validos.");
        }
        if (operador == null || operador.isBlank()) {
            throw new IllegalArgumentException("Operador nao identificado.");
        }

        String motivo = normalizarMotivo(request.motivo());
        String observacao = request.observacao() == null ? null : request.observacao().trim();
        if (observacao != null && observacao.length() > 255) {
            throw new IllegalArgumentException("A observacao deve ter ate 255 caracteres.");
        }
        if (observacao != null && observacao.isEmpty()) observacao = null;

        // Bloqueia a linha do produto para serializar pedidos e ajustes concorrentes.
        List<ProdutoAtual> produtos = jdbc.query("""
                SELECT id_produto, nome, preco, estoque
                  FROM dbo.Produtos WITH (UPDLOCK, HOLDLOCK)
                 WHERE id_produto = ? AND ativo = 1
                """, (rs, rowNum) -> new ProdutoAtual(
                rs.getLong("id_produto"), rs.getString("nome"),
                rs.getBigDecimal("preco"), rs.getInt("estoque")), request.produtoId());

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

        BigDecimal valorTotal = produto.preco().multiply(BigDecimal.valueOf(request.quantidade()));
        Long id = jdbc.queryForObject("""
                INSERT INTO dbo.Desperdicios_Produtos
                    (produto_id, produto_nome, quantidade, motivo, observacao,
                     valor_unitario, valor_total, estoque_antes, estoque_depois, registrado_por)
                OUTPUT INSERTED.id_desperdicio
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, produto.id(), produto.nome(), request.quantidade(), motivo,
                observacao, produto.preco(), valorTotal, produto.estoque(), estoqueDepois, operador);

        return jdbc.queryForObject("""
                SELECT id_desperdicio, produto_id, produto_nome, quantidade, motivo,
                       observacao, valor_unitario, valor_total, estoque_antes,
                       estoque_depois, registrado_por, registrado_em
                  FROM dbo.Desperdicios_Produtos WHERE id_desperdicio = ?
                """, this::mapear, id);
    }

    private String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Informe o motivo do desperdicio.");
        }
        String normalizado = motivo.trim().toUpperCase(Locale.ROOT);
        if (!List.of("VENCIMENTO", "PREPARO", "DANO", "OUTROS").contains(normalizado)) {
            throw new IllegalArgumentException("Motivo invalido. Use VENCIMENTO, PREPARO, DANO ou OUTROS.");
        }
        return normalizado;
    }

    private DesperdicioResponse mapear(ResultSet rs, int rowNum) throws SQLException {
        return new DesperdicioResponse(
                rs.getLong("id_desperdicio"), rs.getLong("produto_id"),
                rs.getString("produto_nome"), rs.getInt("quantidade"),
                rs.getString("motivo"), rs.getString("observacao"),
                rs.getBigDecimal("valor_unitario"), rs.getBigDecimal("valor_total"),
                rs.getInt("estoque_antes"), rs.getInt("estoque_depois"),
                rs.getString("registrado_por"), rs.getTimestamp("registrado_em").toLocalDateTime());
    }

    private record ProdutoAtual(Long id, String nome, BigDecimal preco, int estoque) {}
}
