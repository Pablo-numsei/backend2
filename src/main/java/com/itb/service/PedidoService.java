package com.itb.service;

import com.itb.dto.AvancarStatusRequest;
import com.itb.dto.ItemPedidoRequest;
import com.itb.dto.PedidoCreateRequest;
import org.springframework.dao.DataAccessException;
import com.itb.exception.ResourceNotFoundException;

import com.itb.model.Order;
import com.itb.model.StatusPedido;

import com.itb.repository.OrderRepository;
import com.itb.repository.StatusPedidoRepository;

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
    private final StatusPedidoRepository statusPedidoRepository;
    private final SocketService socketService;
    private final PushNotificationService pushNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    // =========================
    // CRIAR PEDIDO
    // =========================
    public Order criarPedido(PedidoCreateRequest request) {

        String nome = request.nomeCliente() == null ? null : request.nomeCliente().trim();
        if (nome != null && !nome.isEmpty()
                && (nome.length() < 2 || nome.length() > 80
                || nome.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Informe um nome válido de 2 a 80 caracteres.");
        }

        boolean whatsapp = Boolean.TRUE.equals(request.receberWhatsApp());
        String numero = whatsapp ? WhatsAppNotificationService.normalizeBrPhone(request.telefone()) : null;
        if (whatsapp && !whatsAppNotificationService.isConfigured()) {
            throw new IllegalStateException("Atualizações pelo WhatsApp indisponíveis.");
        }

        if (request.itens().stream().map(ItemPedidoRequest::produtoId).distinct().count() != request.itens().size()) {
            throw new IllegalArgumentException("Agrupe as quantidades de produtos repetidos antes de enviar o pedido.");
        }

        StringBuilder sql = new StringBuilder();

        sql.append("DECLARE @itens dbo.TipoItemPedido; ");

        sql.append(
                "INSERT INTO @itens (produto_id, quantidade) VALUES "
        );

        List<Object> parametros = new ArrayList<>();

        for (int i = 0; i < request.itens().size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("(?, ?)");

            ItemPedidoRequest item =
                    request.itens().get(i);

            parametros.add(item.produtoId());
            parametros.add(item.quantidade());
        }

        sql.append("; ");

        sql.append(
                "DECLARE @id_pedido BIGINT; "
        );

        sql.append(
                "EXEC dbo.sp_CriarPedido "
        );

        sql.append(
                "@mesa_id = ?, "
        );

        sql.append(
                "@itens = @itens, "
        );

        sql.append(
                "@criado_por = ?, "
        );

        sql.append(
                "@id_pedido = @id_pedido OUTPUT, @nome_cliente = ?, @telefone_cliente = ?, @whatsapp_consentimento = ?; "
        );

        sql.append(
                "SELECT @id_pedido;"
        );

        parametros.add(
                request.mesaId()
        );

        parametros.add(
                new SqlParameterValue(
                        Types.BIGINT,
                        request.criadoPor()
                )
        );

        parametros.add(new SqlParameterValue(Types.NVARCHAR, nome));
        parametros.add(new SqlParameterValue(Types.VARCHAR, numero));
        parametros.add(whatsapp);

        Object[] args =
                parametros.toArray();
Long pedidoId;

try {

    pedidoId = jdbcTemplate.queryForObject(
            sql.toString(),
            Long.class,
            args
    );

} catch (DataAccessException ex) {

    String mensagemBanco =
            ex.getMostSpecificCause().getMessage();

    if (mensagemBanco != null
            && mensagemBanco.contains(
                    "Mesa inexistente ou inativa"
            )) {

        throw new IllegalArgumentException(
                "Mesa inexistente ou inativa"
        );
    }

    if (mensagemBanco != null
            && mensagemBanco.contains(
                    "Existe produto inexistente"
            )) {

        throw new IllegalStateException(
                "Produto inexistente, indisponível ou sem estoque suficiente"
        );
    }

    if (mensagemBanco != null
            && mensagemBanco.contains(
                    "Status inicial Recebido não está configurado"
            )) {

        throw new IllegalStateException(
                "O status inicial do sistema não está configurado"
        );
    }

    throw ex;
}

        if (pedidoId == null) {
            throw new IllegalStateException(
                    "Não foi possível obter o ID do pedido criado"
            );
        }

        Order pedido = orderRepository
                .findById(pedidoId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Pedido criado, mas não encontrado"
                        )
                );

        // Enfileiramento persistente; falhas de WhatsApp nunca desfazem o pedido.
        whatsAppNotificationService.queueOrderStatus(pedido);

        // Atualiza os clientes conectados via WebSocket
        socketService.notifyOrderUpdate(
                pedido
        );

        return pedido;
    }

    // =========================
    // AVANÇAR STATUS
    // =========================
    public Order avancarStatus(
            Long pedidoId,
            AvancarStatusRequest request
    ) {

        // Verifica se o pedido existe
        // e se não foi excluído logicamente.
        Order pedidoExistente = orderRepository
                .findById(pedidoId)
                .filter(order ->
                        order.getDeletedAt() == null
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Pedido não encontrado"
                        )
                );

        // Verifica se o status enviado existe.
        StatusPedido novoStatus =
                statusPedidoRepository
                        .findByNameIgnoreCase(
                                request.novoStatus()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Status informado não existe"
                                )
                        );

        int ordemAtual =
                pedidoExistente
                        .getStatus()
                        .getFlowOrder();

        int novaOrdem =
                novoStatus
                        .getFlowOrder();

        /*
         * Fluxo:
         *
         * Recebido    = 1
         * Em preparo  = 2
         * Pronto      = 3
         * Entregue    = 4
         *
         * O próximo status precisa ser
         * exatamente atual + 1.
         */
        if (novaOrdem != ordemAtual + 1) {

            throw new IllegalStateException(
                    "Transição de status não permitida: "
                            + pedidoExistente
                                    .getStatus()
                                    .getName()
                            + " → "
                            + novoStatus.getName()
            );
        }

        String sql = """
                EXEC dbo.sp_AvancarStatusPedido
                    @id_pedido = ?,
                    @novo_status = ?,
                    @alterado_por = ?
                """;

        jdbcTemplate.update(
                sql,
                pedidoExistente.getId(),
                novoStatus.getName(),
                new SqlParameterValue(
                        Types.BIGINT,
                        request.alteradoPor()
                )
        );

        Order pedidoAtualizado =
                orderRepository
                        .findById(pedidoId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Pedido não encontrado após atualizar o status"
                                )
                        );

        // Envia a atualização em tempo real.
        socketService.notifyOrderUpdate(
                pedidoAtualizado
        );

        pushNotificationService.notifyOrderStatus(
                pedidoAtualizado
        );
        whatsAppNotificationService.queueOrderStatus(pedidoAtualizado);

        return pedidoAtualizado;
    }
}
