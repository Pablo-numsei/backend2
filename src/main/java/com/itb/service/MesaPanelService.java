package com.itb.service;

import com.itb.dto.MesaPanelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MesaPanelService {
    private final JdbcTemplate jdbcTemplate;

    // EXISTS avoids duplicating orders if legacy data contains multiple payment rows.
    // A confirmed, non-deleted order releases its table only after delivery AND payment.
    private static final String PANEL_SQL = """
            WITH active_orders AS (
                SELECT p.id_pedido, p.mesa_id, s.nome AS order_status, p.valor_total,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM dbo.Pagamentos pg
                           WHERE pg.pedido_id = p.id_pedido AND UPPER(pg.status) = N'PAGO'
                       ) THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END AS paid
                FROM dbo.Pedidos p
                INNER JOIN dbo.Status_Pedido s ON s.id_status = p.status_id
                WHERE p.confirmado = 1 AND p.excluido_em IS NULL
            )
            SELECT m.id_mesa AS table_id, m.numero AS table_number,
                   o.id_pedido AS order_id, o.order_status, o.valor_total AS order_total, o.paid
            FROM dbo.Mesas m
            LEFT JOIN active_orders o
                ON o.mesa_id = m.id_mesa
               AND (o.order_status <> N'Entregue' OR o.paid = 0)
            WHERE m.ativa = 1
            ORDER BY m.numero, m.id_mesa, o.id_pedido
            """;

    public List<MesaPanelResponse> getPanel() {
        return jdbcTemplate.query(PANEL_SQL, (ResultSetExtractor<List<MesaPanelResponse>>) rows -> {
            Map<Long, TableRows> tables = new LinkedHashMap<>();
            while (rows.next()) {
                long tableId = rows.getLong("table_id");
                TableRows table = tables.get(tableId);
                if (table == null) {
                    table = new TableRows(tableId, rows.getInt("table_number"));
                    tables.put(tableId, table);
                }
                Long orderId = rows.getObject("order_id", Long.class);
                if (orderId != null) {
                    table.orders.add(new MesaPanelResponse.OrderSummary(orderId,
                            rows.getString("order_status"), rows.getBigDecimal("order_total"),
                            rows.getBoolean("paid")));
                }
            }
            return tables.values().stream()
                    .map(table -> new MesaPanelResponse(table.id, table.number,
                            table.orders.isEmpty() ? "Livre" : "Ocupada", table.orders))
                    .toList();
        });
    }

    private static final class TableRows {
        private final Long id;
        private final Integer number;
        private final List<MesaPanelResponse.OrderSummary> orders = new ArrayList<>();

        private TableRows(Long id, Integer number) {
            this.id = id;
            this.number = number;
        }
    }
}
