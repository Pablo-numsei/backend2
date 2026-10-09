package com.itb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.dto.PushSubscriptionRequest;
import com.itb.dto.StaffPushSubscriptionRequest;
import com.itb.dto.AtendimentoResponse;
import com.itb.model.Order;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(PushNotificationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public PushNotificationService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${push.vapid.public-key:}") String publicKey,
            @Value("${push.vapid.private-key:}") String privateKey,
            @Value("${push.vapid.subject:mailto:tablehub@localhost}") String subject
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject == null || subject.isBlank()
                ? "mailto:tablehub@localhost"
                : subject.trim();

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void register(PushSubscriptionRequest request) {
        Integer pedidoExiste = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM dbo.Pedidos
                 WHERE id_pedido = ?
                   AND excluido_em IS NULL
                """,
                Integer.class,
                request.pedidoId()
        );

        if (pedidoExiste == null || pedidoExiste == 0) {
            throw new IllegalArgumentException("Pedido não encontrado.");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE dbo.Push_Subscriptions
                   SET pedido_id = ?,
                       p256dh = ?,
                       auth = ?,
                       ativo = 1,
                       alterado_em = SYSUTCDATETIME()
                 WHERE endpoint = ?
                   AND canal = 'PEDIDO'
                """,
                request.pedidoId(),
                request.keys().p256dh(),
                request.keys().auth(),
                request.endpoint()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO dbo.Push_Subscriptions
                        (pedido_id, canal, endpoint, p256dh, auth, ativo)
                    VALUES (?, 'PEDIDO', ?, ?, ?, 1)
                    """,
                    request.pedidoId(),
                    request.endpoint(),
                    request.keys().p256dh(),
                    request.keys().auth()
            );
        }
    }

    public void registerStaff(StaffPushSubscriptionRequest request) {
        int updated = jdbcTemplate.update(
                """
                UPDATE dbo.Push_Subscriptions
                   SET pedido_id = NULL,
                       p256dh = ?,
                       auth = ?,
                       ativo = 1,
                       alterado_em = SYSUTCDATETIME()
                 WHERE endpoint = ?
                   AND canal = 'EQUIPE'
                """,
                request.keys().p256dh(),
                request.keys().auth(),
                request.endpoint()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO dbo.Push_Subscriptions
                        (pedido_id, canal, endpoint, p256dh, auth, ativo)
                    VALUES (NULL, 'EQUIPE', ?, ?, ?, 1)
                    """,
                    request.endpoint(),
                    request.keys().p256dh(),
                    request.keys().auth()
            );
        }
    }

    public void notifyStaffAtendimento(AtendimentoResponse atendimento) {
        try {
            if (atendimento == null || atendimento.id() == null || atendimento.mesa() == null) {
                return;
            }

            String mesa = String.format("%02d", atendimento.mesa());
            String title = "CONTA".equalsIgnoreCase(atendimento.tipo())
                    ? "Mesa " + mesa + " solicitou a conta"
                    : "Mesa " + mesa + " chamou o garçom";

            String detalhe = atendimento.detalhe() == null || atendimento.detalhe().isBlank()
                    ? "Nova solicitação de atendimento."
                    : atendimento.detalhe();

            sendToStaff(
                    title,
                    detalhe,
                    "atendimento-" + atendimento.id()
            );
        } catch (Exception ex) {
            log.error(
                    "Falha no Web Push da equipe. O atendimento foi mantido normalmente.",
                    ex
            );
        }
    }

    public void notifyOrderStatus(Order order) {
        try {
            if (order == null || order.getId() == null || order.getStatus() == null) {
                return;
            }

            String status = order.getStatus().getName();
            String body = switch (status) {
                case "Em preparo" ->
                        "A cozinha começou a preparar o seu pedido #" + order.getId() + ".";
                case "Pronto" ->
                        "Seu pedido #" + order.getId() + " está pronto.";
                case "Entregue" ->
                        "Seu pedido #" + order.getId() + " foi entregue.";
                default ->
                        "O pedido #" + order.getId() + " agora está com status: " + status + ".";
            };

            String statusTag = switch (status) {
                case "Em preparo" -> "em-preparo";
                case "Pronto" -> "pronto";
                case "Entregue" -> "entregue";
                default -> "atualizado";
            };

            sendToOrder(
                    order,
                    "Atualização do pedido",
                    body,
                    "pedido-" + order.getId() + "-" + statusTag
            );
        } catch (Exception ex) {
            log.error(
                    "Falha no Web Push do pedido {}. O status foi mantido normalmente.",
                    order != null ? order.getId() : null,
                    ex
            );
        }
    }

    public void notifyPaymentConfirmed(Order order) {
        try {
            if (order == null || order.getId() == null) {
                return;
            }

            sendToOrder(
                    order,
                    "Pagamento registrado",
                    "O pagamento do pedido #" + order.getId() + " foi registrado no TableHub.",
                    "pagamento-" + order.getId()
            );
        } catch (Exception ex) {
            log.error(
                    "Falha no Web Push do pagamento do pedido {}. O pagamento foi mantido normalmente.",
                    order != null ? order.getId() : null,
                    ex
            );
        }
    }

    private void sendToOrder(
            Order order,
            String title,
            String body,
            String tag
    ) {
        if (!isConfigured()) {
            log.debug("Web Push não configurado; notificação ignorada.");
            return;
        }

        String table = order.getMesa() != null && order.getMesa().getNumber() != null
                ? String.valueOf(order.getMesa().getNumber())
                : "";

        String url = "/acompanhar-pedido?id=%23"
                + order.getId()
                + "&mesa="
                + table;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    Map.of(
                            "title", title,
                            "body", body,
                            "url", url,
                            "tag", tag
                    )
            );
        } catch (JsonProcessingException ex) {
            log.warn("Não foi possível montar o payload da notificação push.", ex);
            return;
        }

        List<SubscriptionRow> subscriptions = jdbcTemplate.query(
                """
                SELECT id_push, endpoint, p256dh, auth
                  FROM dbo.Push_Subscriptions
                 WHERE pedido_id = ?
                   AND canal = 'PEDIDO'
                   AND ativo = 1
                """,
                (rs, rowNum) -> new SubscriptionRow(
                        rs.getLong("id_push"),
                        rs.getString("endpoint"),
                        rs.getString("p256dh"),
                        rs.getString("auth")
                ),
                order.getId()
        );

        for (SubscriptionRow subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    private void sendToStaff(
            String title,
            String body,
            String tag
    ) {
        if (!isConfigured()) {
            log.debug("Web Push não configurado; notificação da equipe ignorada.");
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    Map.of(
                            "title", title,
                            "body", body,
                            "url", "/atendimento",
                            "tag", tag
                    )
            );
        } catch (JsonProcessingException ex) {
            log.warn("Não foi possível montar o payload da notificação da equipe.", ex);
            return;
        }

        List<SubscriptionRow> subscriptions = jdbcTemplate.query(
                """
                SELECT id_push, endpoint, p256dh, auth
                  FROM dbo.Push_Subscriptions
                 WHERE canal = 'EQUIPE'
                   AND ativo = 1
                """,
                (rs, rowNum) -> new SubscriptionRow(
                        rs.getLong("id_push"),
                        rs.getString("endpoint"),
                        rs.getString("p256dh"),
                        rs.getString("auth")
                )
        );

        for (SubscriptionRow subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    private void send(SubscriptionRow subscription, String payload) {
        try {
            PushService pushService =
                    new PushService(publicKey, privateKey, subject);

            Notification notification = new Notification(
                    subscription.endpoint(),
                    subscription.p256dh(),
                    subscription.auth(),
                    payload
            );

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404 || statusCode == 410) {
                jdbcTemplate.update(
                        """
                        UPDATE dbo.Push_Subscriptions
                           SET ativo = 0,
                               alterado_em = SYSUTCDATETIME()
                         WHERE id_push = ?
                        """,
                        subscription.id()
                );
            } else if (statusCode < 200 || statusCode >= 300) {
                log.warn(
                        "Web Push retornou HTTP {} para a inscrição {}.",
                        statusCode,
                        subscription.id()
                );
            }
        } catch (Exception ex) {
            log.warn(
                    "Falha ao enviar Web Push para a inscrição {}.",
                    subscription.id(),
                    ex
            );
        }
    }

    private record SubscriptionRow(
            Long id,
            String endpoint,
            String p256dh,
            String auth
    ) {
    }
}
