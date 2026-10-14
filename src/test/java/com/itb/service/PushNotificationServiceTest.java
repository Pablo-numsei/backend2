package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.dto.PushSubscriptionRequest;
import com.itb.model.Order;
import com.itb.model.StatusPedido;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushNotificationServiceTest {
    private JdbcTemplate jdbc;
    private PushNotificationService service;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        service = new PushNotificationService(jdbc, new ObjectMapper(), "configured", "configured",
                "mailto:example@example.test", mock(PushEndpointValidator.class));
    }

    @AfterEach
    void cleanup() {
        service.shutdown();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidEncryptionKeysCannotCreateDatabaseRecords() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(12L,
                "https://fcm.googleapis.com/example", new PushSubscriptionRequest.Keys("invalid", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void deviceLimitPreventsAdditionalRegistration() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1, 5);
        assertThrows(IllegalStateException.class, () -> service.register(validSubscription()));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void transactionRollbackDoesNotSendNotifications() {
        beginTransaction();
        service.notifyOrderStatus(order());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(jdbc);
    }

    @Test
    void notificationQueriesStartOnlyAfterCommit() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(12L))).thenReturn(List.of());
        beginTransaction();
        service.notifyOrderStatus(order());
        verifyNoInteractions(jdbc);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(jdbc, timeout(1000)).query(anyString(), any(RowMapper.class), eq(12L));
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private Order order() {
        Order order = new Order();
        order.setId(12L);
        StatusPedido status = new StatusPedido();
        status.setName("Recebido");
        order.setStatus(status);
        return order;
    }

    private PushSubscriptionRequest validSubscription() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        byte[] point = new byte[65];
        point[0] = 4;
        byte[] x = key.getW().getAffineX().toByteArray();
        byte[] y = key.getW().getAffineY().toByteArray();
        System.arraycopy(x, Math.max(0, x.length - 32), point, 1 + Math.max(0, 32 - x.length), Math.min(32, x.length));
        System.arraycopy(y, Math.max(0, y.length - 32), point, 33 + Math.max(0, 32 - y.length), Math.min(32, y.length));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new PushSubscriptionRequest(12L, "https://fcm.googleapis.com/example",
                new PushSubscriptionRequest.Keys(encoder.encodeToString(point), encoder.encodeToString(new byte[16])));
    }
}
