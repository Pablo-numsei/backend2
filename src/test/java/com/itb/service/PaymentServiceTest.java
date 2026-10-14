package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.dto.PaymentRequest;
import com.itb.dto.PaymentResponse;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Order;
import com.itb.model.Payment;
import com.itb.repository.OrderRepository;
import com.itb.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final Long ORDER_ID = 41L;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SocketService socketService;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private EntityManager entityManager;
    @InjectMocks private PaymentService paymentService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(ORDER_ID);
        order.setConfirmed(true);
        order.setTotalValue(new BigDecimal("89.90"));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void clientRequestCreatesPendingPaymentWithoutConfirmingReceipt() {
        stubLockedPayments(List.of());
        stubSave();

        Payment payment = paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", null));

        assertEquals("PENDENTE", payment.getStatus());
        assertEquals(order.getTotalValue(), payment.getAmount());
        assertNull(payment.getProcessedAt());
        assertNull(payment.getGatewayReference());
        verify(orderRepository).findByIdForUpdate(ORDER_ID);
        verify(entityManager).refresh(payment);
        verify(pushNotificationService, never()).notifyPaymentConfirmed(any());
    }

    @Test
    void clientCannotAssertGatewayConfirmation() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", "fabricated-reference")));

        verifyNoInteractions(orderRepository, paymentRepository, socketService, pushNotificationService);
    }

    @Test
    void repeatedRequestReusesPendingPayment() {
        Payment pending = payment("PENDENTE");
        stubLockedPayments(List.of(pending));

        assertSame(pending, paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", null)));

        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(socketService, pushNotificationService);
    }

    @Test
    void changingMethodUpdatesSamePendingPayment() {
        Payment pending = payment("PENDENTE");
        stubLockedPayments(List.of(pending));
        stubSave();

        Payment result = paymentService.processPayment(ORDER_ID, new PaymentRequest("CREDITO", null));

        assertSame(pending, result);
        assertEquals("CREDITO", result.getMethod());
        assertEquals("PENDENTE", result.getStatus());
        verify(paymentRepository).saveAndFlush(pending);
        verify(pushNotificationService, never()).notifyPaymentConfirmed(any());
    }

    @Test
    void alreadyPaidRequestDoesNotCreateAnotherPayment() {
        Payment paid = payment("PAGO");
        stubLockedPayments(List.of(paid));

        assertSame(paid, paymentService.processPayment(ORDER_ID, new PaymentRequest("DEBITO", null)));
        assertEquals("PIX", paid.getMethod());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void manualConfirmationSchedulesPushBeforeCommitAndPublishesSocketAfterCommit() {
        Payment pending = payment("PENDENTE");
        stubLockedPayments(List.of(pending));
        stubSave();
        TransactionSynchronizationManager.initSynchronization();

        Payment paid = paymentService.confirmPayment(ORDER_ID, "caixa@tablehub.test");

        assertEquals("PAGO", paid.getStatus());
        assertNotNull(paid.getProcessedAt());
        verifyNoInteractions(socketService);
        verify(pushNotificationService).notifyPaymentConfirmed(order);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(socketService).notifyOrderUpdate(order);
        verifyNoMoreInteractions(pushNotificationService);
    }

    @Test
    void repeatedManualConfirmationDoesNotWriteOrNotifyAgain() {
        Payment paid = payment("PAGO");
        stubLockedPayments(List.of(paid));

        assertSame(paid, paymentService.confirmPayment(ORDER_ID, "caixa@tablehub.test"));

        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(socketService, pushNotificationService);
    }

    @Test
    void cannotConfirmWithoutPendingRequest() {
        stubLockedPayments(List.of());

        assertThrows(IllegalStateException.class,
                () -> paymentService.confirmPayment(ORDER_ID, "caixa@tablehub.test"));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotConfirmDifferentAmount() {
        Payment pending = payment("PENDENTE");
        pending.setAmount(new BigDecimal("1.00"));
        stubLockedPayments(List.of(pending));

        assertThrows(IllegalStateException.class,
                () -> paymentService.confirmPayment(ORDER_ID, "caixa@tablehub.test"));
        assertEquals("PENDENTE", pending.getStatus());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void legacyDuplicateActivePaymentsRequireCashierReview() {
        stubLockedPayments(List.of(payment("PAGO"), payment("PENDENTE")));

        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", null)));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelsPendingManualRecordWithoutClaimingPaymentSuccess() {
        Payment pending = payment("PENDENTE");
        stubLockedPayments(List.of(pending));
        stubSave();

        assertEquals("CANCELADO", paymentService.cancelPayment(ORDER_ID, "caixa@tablehub.test").getStatus());
        verify(pushNotificationService, never()).notifyPaymentConfirmed(any());
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        Payment cancelled = payment("CANCELADO");
        stubLockedPayments(List.of(cancelled));

        assertSame(cancelled, paymentService.cancelPayment(ORDER_ID, "caixa@tablehub.test"));
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void gatewayRecordCannotBeCancelledAsIfRefunded() {
        Payment paid = payment("PAGO");
        paid.setGatewayReference("legacy-provider-reference");
        stubLockedPayments(List.of(paid));

        assertThrows(IllegalStateException.class,
                () -> paymentService.cancelPayment(ORDER_ID, "caixa@tablehub.test"));
        assertEquals("PAGO", paid.getStatus());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletedOrderCannotRequestPayment() {
        order.setDeletedAt(LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", null)));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void unconfirmedOrderCannotRequestPayment() {
        order.setConfirmed(false);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayment(ORDER_ID, new PaymentRequest("PIX", null)));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void getReturnsNoPaymentForOrderWithoutRecords() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdOrderByIdDesc(ORDER_ID)).thenReturn(List.of());

        assertNull(paymentService.getPayment(ORDER_ID));
    }

    @Test
    void paymentResponseDoesNotExposeOrderEntityOrGatewayReference() {
        var json = new ObjectMapper().valueToTree(PaymentResponse.from(payment("PENDENTE")));

        assertEquals(ORDER_ID.longValue(), json.get("orderId").asLong());
        assertFalse(json.has("order"));
        assertFalse(json.has("gatewayReference"));
    }

    private Payment payment(String status) {
        Payment payment = new Payment();
        payment.setId(101L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalValue());
        payment.setMethod("PIX");
        payment.setStatus(status);
        return payment;
    }

    private void stubLockedPayments(List<Payment> payments) {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdOrderByIdDesc(ORDER_ID)).thenReturn(payments);
    }

    private void stubSave() {
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(101L);
            }
            return payment;
        });
    }
}
