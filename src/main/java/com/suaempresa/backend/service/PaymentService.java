package com.suaempresa.backend.service;

import com.suaempresa.backend.model.Order;
import com.suaempresa.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Equivalente ao antigo services/paymentService.js
 * Aqui vai toda a logica de negocio de pagamento (integracao com
 * gateway de pagamento, validacoes, etc.)
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final SocketService socketService;

    /**
     * Processa o pagamento de um pedido.
     * Substitua o corpo do metodo pela integracao real
     * (Stripe, Mercado Pago, PagSeguro, etc.)
     */
    public Order processPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido nao encontrado: " + orderId));

        // TODO: integrar com o gateway de pagamento real aqui

        order.setStatus(Order.OrderStatus.PAID);
        Order updatedOrder = orderRepository.save(order);

        // Notifica clientes conectados via WebSocket, assim como no socketService.js
        socketService.notifyOrderUpdate(updatedOrder);

        return updatedOrder;
    }

    public Order cancelPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido nao encontrado: " + orderId));

        order.setStatus(Order.OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);

        socketService.notifyOrderUpdate(updatedOrder);

        return updatedOrder;
    }
}
