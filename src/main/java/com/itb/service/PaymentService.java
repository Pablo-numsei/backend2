package com.itb.service;

import com.itb.dto.PaymentRequest;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Order;
import com.itb.model.Payment;
import com.itb.repository.OrderRepository;
import com.itb.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SocketService socketService;

    @Transactional
    public Payment processPayment(
            Long orderId,
            PaymentRequest request
    ) {

        Order order = orderRepository.findById(orderId)
                .filter(existingOrder ->
                        existingOrder.getDeletedAt() == null
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Pedido não encontrado"
                        )
                );

        if (!Boolean.TRUE.equals(order.getConfirmed())) {
            throw new IllegalStateException(
                    "O pedido ainda não foi confirmado"
            );
        }

        Payment payment = new Payment();

        payment.setOrder(order);

        payment.setAmount(
                order.getTotalValue()
        );

        payment.setMethod(
                request.method()
        );

        payment.setStatus("PAGO");

        payment.setGatewayReference(
                request.gatewayReference()
        );

        payment.setProcessedAt(
                LocalDateTime.now()
        );

        Payment savedPayment =
                paymentRepository.save(payment);

        socketService.notifyOrderUpdate(order);

        return savedPayment;
    }

    @Transactional
    public Payment cancelPayment(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .filter(existingOrder ->
                        existingOrder.getDeletedAt() == null
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Pedido não encontrado"
                        )
                );

        Payment payment = paymentRepository
                .findTopByOrder_IdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Nenhum pagamento encontrado para este pedido"
                        )
                );

        if ("CANCELADO".equalsIgnoreCase(
                payment.getStatus()
        )) {
            throw new IllegalStateException(
                    "O pagamento já está cancelado"
            );
        }

        if (!"PAGO".equalsIgnoreCase(
                payment.getStatus()
        )) {
            throw new IllegalStateException(
                    "Somente pagamentos pagos podem ser cancelados"
            );
        }

        payment.setStatus("CANCELADO");

        Payment savedPayment =
                paymentRepository.save(payment);

        socketService.notifyOrderUpdate(order);

        return savedPayment;
    }
}