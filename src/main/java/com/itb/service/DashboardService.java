package com.itb.service;

import com.itb.dto.DashboardResumoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardResumoResponse getResumo() {

        long pedidosHoje = numberOrZero(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM dbo.Pedidos p
                        WHERE p.excluido_em IS NULL
                          AND p.criado_em >= CONVERT(date, GETDATE())
                          AND p.criado_em < DATEADD(day, 1, CONVERT(date, GETDATE()))
                        """,
                        Long.class
                )
        );

        BigDecimal faturamentoHoje = decimalOrZero(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(SUM(pg.valor), 0)
                        FROM dbo.Pagamentos pg
                        WHERE UPPER(pg.status) = 'PAGO'
                          AND pg.processado_em >= CONVERT(date, GETDATE())
                          AND pg.processado_em < DATEADD(day, 1, CONVERT(date, GETDATE()))
                        """,
                        BigDecimal.class
                )
        );

        long pedidosPagosHoje = numberOrZero(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT pg.pedido_id)
                        FROM dbo.Pagamentos pg
                        WHERE UPPER(pg.status) = 'PAGO'
                          AND pg.processado_em >= CONVERT(date, GETDATE())
                          AND pg.processado_em < DATEADD(day, 1, CONVERT(date, GETDATE()))
                        """,
                        Long.class
                )
        );

        BigDecimal ticketMedio = pedidosPagosHoje == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : faturamentoHoje.divide(
                        BigDecimal.valueOf(pedidosPagosHoje),
                        2,
                        RoundingMode.HALF_UP
                );

        long totalMesas = numberOrZero(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM dbo.Mesas
                        WHERE ativa = 1
                        """,
                        Long.class
                )
        );

        long mesasOcupadas = numberOrZero(
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT p.mesa_id)
                        FROM dbo.Pedidos p
                        INNER JOIN dbo.Status_Pedido s
                            ON s.id_status = p.status_id
                        INNER JOIN dbo.Mesas m
                            ON m.id_mesa = p.mesa_id
                        WHERE p.excluido_em IS NULL
                          AND p.confirmado = 1
                          AND m.ativa = 1
                          AND s.nome <> N'Entregue'
                        """,
                        Long.class
                )
        );

        List<DashboardResumoResponse.StatusResumo> pedidosPorStatus =
                jdbcTemplate.query(
                        """
                        SELECT
                            s.nome AS status,
                            COUNT(p.id_pedido) AS quantidade
                        FROM dbo.Status_Pedido s
                        LEFT JOIN dbo.Pedidos p
                            ON p.status_id = s.id_status
                           AND p.excluido_em IS NULL
                           AND p.criado_em >= CONVERT(date, GETDATE())
                           AND p.criado_em < DATEADD(day, 1, CONVERT(date, GETDATE()))
                        GROUP BY s.id_status, s.nome, s.ordem_fluxo
                        ORDER BY s.ordem_fluxo
                        """,
                        (rs, rowNum) ->
                                new DashboardResumoResponse.StatusResumo(
                                        rs.getString("status"),
                                        rs.getLong("quantidade")
                                )
                );

        List<DashboardResumoResponse.PedidoRecente> pedidosRecentes =
                jdbcTemplate.query(
                        """
                        SELECT TOP 5
                            p.id_pedido,
                            m.numero AS mesa_numero,
                            s.nome AS status,
                            p.valor_total,
                            p.criado_em
                        FROM dbo.Pedidos p
                        INNER JOIN dbo.Mesas m
                            ON m.id_mesa = p.mesa_id
                        INNER JOIN dbo.Status_Pedido s
                            ON s.id_status = p.status_id
                        WHERE p.excluido_em IS NULL
                        ORDER BY p.criado_em DESC, p.id_pedido DESC
                        """,
                        (rs, rowNum) ->
                                new DashboardResumoResponse.PedidoRecente(
                                        rs.getLong("id_pedido"),
                                        rs.getInt("mesa_numero"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("valor_total"),
                                        rs.getTimestamp("criado_em").toLocalDateTime()
                                )
                );

        Map<Integer, Long> movimentoMap = new HashMap<>();

        jdbcTemplate.query(
                """
                SELECT
                    DATEPART(HOUR, p.criado_em) AS hora,
                    COUNT(*) AS quantidade
                FROM dbo.Pedidos p
                WHERE p.excluido_em IS NULL
                  AND p.criado_em >= CONVERT(date, GETDATE())
                  AND p.criado_em < DATEADD(day, 1, CONVERT(date, GETDATE()))
                GROUP BY DATEPART(HOUR, p.criado_em)
                ORDER BY hora
                """,
                rs -> movimentoMap.put(
                        rs.getInt("hora"),
                        rs.getLong("quantidade")
                )
        );

        List<DashboardResumoResponse.MovimentoHora> movimentoPorHora =
                new ArrayList<>();

        for (int hora = 0; hora < 24; hora++) {
            movimentoPorHora.add(
                    new DashboardResumoResponse.MovimentoHora(
                            hora,
                            movimentoMap.getOrDefault(hora, 0L)
                    )
            );
        }

        List<DashboardResumoResponse.MesaResumo> mesas =
                jdbcTemplate.query(
                        """
                        SELECT TOP 8
                            m.numero,
                            CASE
                                WHEN EXISTS (
                                    SELECT 1
                                    FROM dbo.Pedidos p
                                    INNER JOIN dbo.Status_Pedido s
                                        ON s.id_status = p.status_id
                                    WHERE p.mesa_id = m.id_mesa
                                      AND p.excluido_em IS NULL
                                      AND p.confirmado = 1
                                      AND s.nome <> N'Entregue'
                                )
                                THEN N'Ocupada'
                                ELSE N'Livre'
                            END AS status
                        FROM dbo.Mesas m
                        WHERE m.ativa = 1
                        ORDER BY m.numero
                        """,
                        (rs, rowNum) ->
                                new DashboardResumoResponse.MesaResumo(
                                        rs.getInt("numero"),
                                        rs.getString("status")
                                )
                );

        return new DashboardResumoResponse(
                pedidosHoje,
                faturamentoHoje.setScale(2, RoundingMode.HALF_UP),
                ticketMedio,
                mesasOcupadas,
                totalMesas,
                pedidosPorStatus,
                pedidosRecentes,
                movimentoPorHora,
                mesas
        );
    }

    private long numberOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal decimalOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
