package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.model.Order;
import com.itb.model.Payment;
import com.itb.repository.OrderRepository;
import com.itb.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentNotificationTransactionTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final SocketService socket = mock(SocketService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ThreadPoolExecutor notifications = mock(ThreadPoolExecutor.class);

    private PushNotificationService push;
    private PaymentService service;
    private Order order;

    @BeforeEach
    void setup() {
        push = new PushNotificationService(jdbc, new ObjectMapper(), "configured", "configured",
                "mailto:example@example.test", mock(PushEndpointValidator.class));
        // Capture dispatch without running tasks, opening connections, or starting workers.
        ReflectionTestUtils.setField(push, "notifications", notifications);
        service = new PaymentService(orders, payments, socket, push, mock(EntityManager.class));
        order = new Order();
        order.setId(41L);
        order.setConfirmed(true);
        order.setTotalValue(new BigDecimal("89.90"));
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        push.shutdown();
    }

    @Test
    void registeringPushInsideAfterCommitMissesSpringCommitSnapshot() {
        beginTransaction();
        // Reproduce the previous PaymentService callback using Spring's real snapshot.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push.notifyPaymentConfirmed(order);
            }
        });

        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertEquals(2, TransactionSynchronizationManager.getSynchronizations().size());
        verifyNoInteractions(notifications, jdbc);
    }

    @Test
    void confirmedPaymentDispatchesPushAndSocketExactlyOnceAfterCommit() {
        stubPendingPayment();
        beginTransaction();

        service.confirmPayment(order.getId(), "caixa@tablehub.test");

        assertEquals(2, TransactionSynchronizationManager.getSynchronizations().size());
        verifyNoInteractions(notifications, jdbc, socket);

        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(notifications).execute(any(Runnable.class));
        verifyNoMoreInteractions(notifications);
        verify(socket).notifyOrderUpdate(order);
        verifyNoMoreInteractions(socket);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rolledBackConfirmationDoesNotDispatchPushOrSocket() {
        stubPendingPayment();
        beginTransaction();

        service.confirmPayment(order.getId(), "caixa@tablehub.test");
        TransactionSynchronizationUtils.triggerBeforeCompletion();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(notifications, jdbc, socket);
    }

    @Test
    void socketFailureAfterCommitDoesNotLosePushDispatch() {
        stubPendingPayment();
        doThrow(new IllegalStateException("socket unavailable")).when(socket).notifyOrderUpdate(order);
        beginTransaction();
        service.confirmPayment(order.getId(), "caixa@tablehub.test");

        assertDoesNotThrow(TransactionSynchronizationUtils::triggerAfterCommit);

        verify(notifications).execute(any(Runnable.class));
        verifyNoInteractions(jdbc);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void stubPendingPayment() {
        Payment pending = new Payment();
        pending.setId(101L);
        pending.setOrder(order);
        pending.setAmount(order.getTotalValue());
        pending.setMethod("PIX");
        pending.setStatus("PENDENTE");
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrder_IdOrderByIdDesc(order.getId())).thenReturn(List.of(pending));
        when(payments.saveAndFlush(pending)).thenReturn(pending);
    }
}
