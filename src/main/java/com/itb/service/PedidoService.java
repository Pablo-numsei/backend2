package com.itb.service;

import com.itb.dto.AvancarStatusRequest;
import com.itb.dto.ItemPedidoRequest;
import com.itb.dto.PedidoCreateRequest;
import com.itb.model.Order;
import com.itb.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderRepository orderRepository;

    public Order criarPedido(PedidoCreateRequest request) {

        StringBuilder sql = new StringBuilder();

        sql.append("DECLARE @itens dbo.TipoItemPedido; ");
        sql.append("INSERT INTO @itens (produto_id, quantidade) VALUES ");

        List<Object> parametros = new ArrayList<>();

        for (int i = 0; i < request.itens().size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("(?, ?)");

            ItemPedidoRequest item = request.itens().get(i);

            parametros.add(item.produtoId());
            parametros.add(item.quantidade());
        }

        sql.append("; ");
        sql.append("DECLARE @id_pedido BIGINT; ");
        sql.append("EXEC dbo.sp_CriarPedido ");
        sql.append("@mesa_id = ?, ");
        sql.append("@itens = @itens, ");
        sql.append("@criado_por = ?, ");
        sql.append("@id_pedido = @id_pedido OUTPUT; ");
        sql.append("SELECT @id_pedido;");

        parametros.add(request.mesaId());

        parametros.add(
                new SqlParameterValue(
                        Types.BIGINT,
                        request.criadoPor()
                )
        );

        Object[] args = parametros.toArray();

String consultaSql = java.util.Objects.requireNonNull(
        sql.toString()
);

Long pedidoId = jdbcTemplate.queryForObject(
        consultaSql,
        Long.class,
        args
);
        return orderRepository
                .findById(pedidoId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Pedido criado, mas nao encontrado"
                        )
                );
    }





    public Order avancarStatus(
        Long pedidoId,
        AvancarStatusRequest request) {

    String sql = """
            EXEC dbo.sp_AvancarStatusPedido
                @id_pedido = ?,
                @novo_status = ?,
                @alterado_por = ?
            """;

    jdbcTemplate.update(
            sql,
            pedidoId,
            request.novoStatus(),
            new SqlParameterValue(
                    Types.BIGINT,
                    request.alteradoPor()
            )
    );

    return orderRepository.findById(pedidoId)
            .orElseThrow(() ->
                    new IllegalStateException(
                            "Pedido nao encontrado apos atualizar o status"
                    )
            );
}
}