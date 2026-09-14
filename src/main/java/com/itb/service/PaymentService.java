package com.itb.service;

import com.itb.model.Order;
import com.itb.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final SocketService socketService;

    public Order processPayment(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Pedido não encontrado")
                );

        socketService.notifyOrderUpdate(order);

        return order;
    }

    public Order cancelPayment(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Pedido não encontrado")
                );

        socketService.notifyOrderUpdate(order);

        return order;
    }
}