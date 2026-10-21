package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.model.Order;
import com.itb.model.StatusPedido;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppNotificationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private WhatsAppNotificationService service(boolean enabled) {
        return new WhatsAppNotificationService(
                jdbc, mapper, enabled, "synthetic-not-a-real-token",
                "12345678901", "v23.0", "tablehub_status_pedido", "pt_BR");
    }

    @Test
    void phoneOnlyAcceptsBrazilianDddWithCountryCode() {
        assertEquals("5511987654321", WhatsAppNotificationService.normalizeBrPhone("(11) 98765-4321"));
        assertEquals("5511987654321", WhatsAppNotificationService.normalizeBrPhone("+55 11 98765-4321"));
        assertThrows(IllegalArgumentException.class,
                () -> WhatsAppNotificationService.normalizeBrPhone(""));
        assertThrows(IllegalArgumentException.class,
                () -> WhatsAppNotificationService.normalizeBrPhone("1234"));
        assertThrows(IllegalArgumentException.class,
                () -> WhatsAppNotificationService.normalizeBrPhone("+1 202 555 0120"));
    }

    @Test
    void notConfiguredNeverQueriesOrderOrSendsAnything() {
        var disabled = service(false);
        assertFalse(disabled.isConfigured());
        Order order = new Order();
        order.setId(44L);
        StatusPedido status = new StatusPedido();
        status.setName("Pronto");
        order.setStatus(status);
        disabled.queueOrderStatus(order);
        disabled.deliverPending();
        verifyNoInteractions(jdbc);
    }

    @Test
    void configuredServiceQueuesStatusWithoutExposingPhone() {
        var enabled = service(true);
        assertTrue(enabled.isConfigured());
        Order order = new Order();
        order.setId(44L);
        StatusPedido status = new StatusPedido();
        status.setName("Em preparo");
        order.setStatus(status);
        enabled.queueOrderStatus(order);
        verify(jdbc).update(contains("WhatsApp_Outbox"), eq("Em preparo"), eq(44L), eq("Em preparo"));
    }

    @Test
    void optOutRemovesPhoneAndPendingMessages() {
        service(false).cancelConsent(44L);
        verify(jdbc).update(contains("SET whatsapp_consentimento = 0"), eq(44L));
        verify(jdbc).update(contains("UPDATE dbo.WhatsApp_Outbox"), eq(44L));
    }
}
