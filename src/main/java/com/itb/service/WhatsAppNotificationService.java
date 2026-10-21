package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.model.Order;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Opt-in utility notifications using Meta's WhatsApp Cloud API.
 * SQL outbox decouples ordering from network calls; message accepted != delivered.
 * Never logs tokens, customer phone numbers, or message bodies.
 */
@Service
public class WhatsAppNotificationService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);
    private static final Pattern GRAPH_VERSION = Pattern.compile("v[0-9]+\\.[0-9]+");
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_]+");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final boolean enabled;
    private final String token;
    private final String phoneId;
    private final String graphVersion;
    private final String template;
    private final String language;

    public WhatsAppNotificationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${whatsapp.cloud.enabled:false}") boolean enabled,
            @Value("${whatsapp.cloud.token:}") String token,
            @Value("${whatsapp.cloud.phone-id:}") String phoneId,
            @Value("${whatsapp.cloud.api-version:v23.0}") String graphVersion,
            @Value("${whatsapp.cloud.template:tablehub_status_pedido}") String template,
            @Value("${whatsapp.cloud.template-language:pt_BR}") String language
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.phoneId = phoneId == null ? "" : phoneId.trim();
        this.graphVersion = graphVersion == null ? "" : graphVersion.trim();
        this.template = template == null ? "" : template.trim();
        this.language = language == null ? "" : language.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Only Brazilian numbers: DDD + 8/9 digits, optionally prefixed by +55. */
    public static String normalizeBrPhone(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Informe um telefone para receber atualizações no WhatsApp.");
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() == 10 || digits.length() == 11) digits = "55" + digits;
        if (!digits.matches("^55[1-9][0-9][2-9][0-9]{7,8}$")) {
            throw new IllegalArgumentException("Digite um telefone brasileiro válido com DDD.");
        }
        return digits;
    }

    public boolean isConfigured() {
        return enabled && !token.isBlank() && phoneId.matches("[0-9]{5,24}")
                && GRAPH_VERSION.matcher(graphVersion).matches()
                && SAFE_NAME.matcher(template).matches()
                && language.matches("[a-z]{2}_[A-Z]{2}");
    }

    /** Enqueue only if customer expressly opted in. Duplicate status never requeues. */
    public void queueOrderStatus(Order order) {
        if (!isConfigured() || order == null || order.getId() == null
                || order.getStatus() == null) return;
        try {
            jdbc.update("""
                INSERT INTO dbo.WhatsApp_Outbox (pedido_id, status_pedido)
                SELECT p.id_pedido, ?
                  FROM dbo.Pedidos p
                 WHERE p.id_pedido = ?
                   AND p.whatsapp_consentimento = 1
                   AND p.whatsapp_numero IS NOT NULL
                   AND p.excluido_em IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM dbo.WhatsApp_Outbox w
                        WHERE w.pedido_id = p.id_pedido AND w.status_pedido = ?
                   )
                """, order.getStatus().getName(), order.getId(), order.getStatus().getName());
        } catch (Exception ex) {
            log.warn("WhatsApp: falha ao enfileirar evento do pedido {}, sem afetar a operação.", order.getId());
        }
    }

    /** Order-scoped token validated in controller before calling this method. */
    public void cancelConsent(Long id) {
        jdbc.update("""
            UPDATE dbo.Pedidos
               SET whatsapp_consentimento = 0, whatsapp_numero = NULL,
                   whatsapp_consentido_em = NULL
             WHERE id_pedido = ?
            """, id);
        jdbc.update("""
            UPDATE dbo.WhatsApp_Outbox
               SET situacao = 'FALHA', reservado_ate = NULL
             WHERE pedido_id = ? AND situacao = 'PENDENTE'
            """, id);
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void deliverPending() {
        if (!isConfigured()) return;
        List<Pending> batch;
        try {
            batch = jdbc.query("""
                ;WITH proximos AS (
                  SELECT TOP (5) *
                    FROM dbo.WhatsApp_Outbox WITH (ROWLOCK, READPAST, UPDLOCK)
                   WHERE tentativas < 5
                     AND (
                        (situacao = 'PENDENTE' AND proxima_tentativa <= SYSUTCDATETIME())
                        OR (situacao = 'PROCESSANDO' AND reservado_ate < SYSUTCDATETIME())
                     )
                   ORDER BY id_notificacao
                )
                UPDATE proximos
                   SET situacao = 'PROCESSANDO',
                       reservado_ate = DATEADD(SECOND, 90, SYSUTCDATETIME())
                OUTPUT INSERTED.id_notificacao, INSERTED.pedido_id, INSERTED.status_pedido, INSERTED.tentativas
                """, (rs, n) -> new Pending(
                    rs.getLong("id_notificacao"), rs.getLong("pedido_id"),
                    rs.getString("status_pedido"), rs.getInt("tentativas")));
        } catch (Exception ex) {
            log.warn("WhatsApp: fila indisponível; tentando novamente mais tarde.");
            return;
        }
        for (Pending item : batch) {
            dispatch(item);
        }
    }

    private void dispatch(Pending item) {
        try {
            // Check consent again immediately before sending; opting out prevents new sends.
            List<Recipient> rows = jdbc.query("""
                SELECT nome_cliente, whatsapp_numero FROM dbo.Pedidos
                 WHERE id_pedido = ? AND whatsapp_consentimento = 1
                   AND whatsapp_numero IS NOT NULL AND excluido_em IS NULL
                """, (rs, n) -> new Recipient(rs.getString(1), rs.getString(2)), item.orderId());
            if (rows.isEmpty()) {
                finish(item.id(), "FALHA");
                return;
            }

            Recipient recipient = rows.get(0);
            String body = mapper.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp",
                    "to", recipient.phone(),
                    "type", "template",
                    "template", Map.of(
                            "name", template,
                            "language", Map.of("code", language),
                            "components", List.of(Map.of(
                                    "type", "body",
                                    "parameters", List.of(
                                            Map.of("type", "text", "text",
                                                    recipient.name() == null ? "Cliente" : recipient.name()),
                                            Map.of("type", "text", "text", String.valueOf(item.orderId())),
                                            Map.of("type", "text", "text", item.status())
                                    )
                            ))
                    )
            ));
            URI uri = URI.create("https://graph.facebook.com/" + graphVersion + "/" + phoneId + "/messages");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Accepted by Meta, not yet confirmed delivered; webhook is a future enhancement.
                finish(item.id(), "ACEITO");
            } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                retry(item);
                log.warn("WhatsApp: indisponibilidade temporária HTTP {} para pedido {}.",
                        response.statusCode(), item.orderId());
            } else {
                finish(item.id(), "FALHA");
                log.warn("WhatsApp: modelo ou credenciais rejeitados (HTTP {}) para pedido {}.",
                        response.statusCode(), item.orderId());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            retry(item);
        } catch (Exception failure) {
            retry(item);
            log.warn("WhatsApp: envio adiado para pedido {}.", item.orderId());
        }
    }

    private void finish(long id, String situation) {
        jdbc.update("""
            UPDATE dbo.WhatsApp_Outbox
               SET situacao = ?, reservado_ate = NULL,
                   aceito_em = CASE WHEN ? = 'ACEITO' THEN SYSUTCDATETIME() ELSE aceito_em END
             WHERE id_notificacao = ?
            """, situation, situation, id);
    }

    private void retry(Pending item) {
        int attempts = item.attempts() + 1;
        int seconds = Math.min(900, 15 * (1 << Math.min(attempts, 6)));
        jdbc.update("""
            UPDATE dbo.WhatsApp_Outbox
               SET tentativas = ?, situacao = ?,
                   proxima_tentativa = DATEADD(SECOND, ?, SYSUTCDATETIME()), reservado_ate = NULL
             WHERE id_notificacao = ?
            """, attempts, attempts >= 5 ? "FALHA" : "PENDENTE", seconds, item.id());
    }

    private record Pending(long id, long orderId, String status, int attempts) {}
    private record Recipient(String name, String phone) {}
}
