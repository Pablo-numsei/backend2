package com.itb.service;

import com.itb.dto.PaymentRequest;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Order;
import com.itb.model.Payment;
import com.itb.repository.OrderRepository;
import com.itb.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SocketService socketService;
    private final PushNotificationService pushNotificationService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Payment getPayment(Long orderId) {
        orderRepository.findById(orderId)
                .filter(order -> order.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        return currentPayment(paymentRepository.findByOrder_IdOrderByIdDesc(orderId));
    }

    /** Solicita conferência no caixa; uma requisição do cliente nunca comprova pagamento. */
    @Transactional
    public Payment processPayment(Long orderId, PaymentRequest request) {
        if (request.gatewayReference() != null && !request.gatewayReference().isEmpty()) {
            throw new IllegalArgumentException("A confirmação de um gateway não pode ser enviada pelo cliente");
        }

        Order order = lockOrder(orderId);
        requireConfirmed(order);

        Payment active = activePayment(paymentRepository.findByOrder_IdOrderByIdDesc(orderId));
        if (active != null) {
            if ("PENDENTE".equalsIgnoreCase(active.getStatus())
                    && !request.method().equals(active.getMethod())) {
                active.setMethod(request.method());
                return paymentRepository.saveAndFlush(active);
            }
            return active;
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalValue());
        payment.setMethod(request.method());
        payment.setStatus("PENDENTE");
        Payment saved = paymentRepository.saveAndFlush(payment);
        entityManager.refresh(saved);
        notifyAfterCommit(order, false);
        return saved;
    }

    /** A equipe confirma manualmente o recebimento; não representa integração com gateway. */
    @Transactional
    public Payment confirmPayment(Long orderId, String actor) {
        Order order = lockOrder(orderId);
        requireConfirmed(order);
        Payment payment = activePayment(paymentRepository.findByOrder_IdOrderByIdDesc(orderId));
        if (payment == null) {
            throw new IllegalStateException("Nenhuma solicitação de pagamento pendente para este pedido");
        }
        if ("PAGO".equalsIgnoreCase(payment.getStatus())) {
            return payment;
        }
        if (payment.getAmount().compareTo(order.getTotalValue()) != 0) {
            throw new IllegalStateException("O valor da solicitação difere do pedido; confira o pagamento no caixa");
        }

        payment.setStatus("PAGO");
        payment.setProcessedAt(LocalDateTime.now());
        Payment saved = paymentRepository.saveAndFlush(payment);
        log.info("Confirmação manual de pagamento: pedido={}, pagamento={}, operador={}",
                orderId, saved.getId(), actor);
        notifyAfterCommit(order, true);
        return saved;
    }

    /** Cancelamento de registro manual; a operação não efetua estorno bancário. */
    @Transactional
    public Payment cancelPayment(Long orderId, String actor) {
        Order order = lockOrder(orderId);
        List<Payment> payments = paymentRepository.findByOrder_IdOrderByIdDesc(orderId);
        Payment payment = activePayment(payments);
        if (payment == null) {
            Payment latest = payments.isEmpty() ? null : payments.getFirst();
            if (latest != null && "CANCELADO".equalsIgnoreCase(latest.getStatus())) {
                return latest;
            }
            throw new IllegalStateException("Nenhum pagamento ativo encontrado para este pedido");
        }
        if (payment.getGatewayReference() != null && !payment.getGatewayReference().isBlank()) {
            throw new IllegalStateException("Pagamento com referência de gateway exige conferência e estorno no provedor");
        }

        payment.setStatus("CANCELADO");
        Payment saved = paymentRepository.saveAndFlush(payment);
        log.info("Cancelamento de registro manual: pedido={}, pagamento={}, operador={}",
                orderId, saved.getId(), actor);
        notifyAfterCommit(order, false);
        return saved;
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
    }

    private void requireConfirmed(Order order) {
        if (!Boolean.TRUE.equals(order.getConfirmed())) {
            throw new IllegalStateException("O pedido ainda não foi confirmado");
        }
    }

    private Payment currentPayment(List<Payment> payments) {
        Payment active = activePayment(payments);
        return active != null ? active : payments.isEmpty() ? null : payments.getFirst();
    }

    private Payment activePayment(List<Payment> payments) {
        List<Payment> active = payments.stream()
                .filter(payment -> "PENDENTE".equalsIgnoreCase(payment.getStatus())
                        || "PAGO".equalsIgnoreCase(payment.getStatus()))
                .toList();
        if (active.size() > 1) {
            throw new IllegalStateException("Existem múltiplos pagamentos ativos; solicite a conferência do caixa");
        }
        return active.isEmpty() ? null : active.getFirst();
    }

    private void notifyAfterCommit(Order order, boolean confirmed) {
        if (confirmed) {
            try {
                // PushNotificationService registers its own afterCommit callback.
                pushNotificationService.notifyPaymentConfirmed(order);
            } catch (Exception ex) {
                log.warn("Falha ao agendar notificação do pagamento do pedido {}", order.getId(), ex);
            }
        }
        Runnable notify = () -> {
            try {
                socketService.notifyOrderUpdate(order);
            } catch (Exception ex) {
                log.warn("Falha ao notificar pagamento do pedido {} após confirmação da transação", order.getId(), ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notify.run();
                }
            });
        } else {
            notify.run();
        }
    }
}
