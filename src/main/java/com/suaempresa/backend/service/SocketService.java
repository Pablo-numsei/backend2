package com.suaempresa.backend.service;

import com.suaempresa.backend.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Equivalente ao antigo services/socketService.js
 * Usa STOMP sobre WebSocket (configurado em WebSocketConfig.java)
 * ao inves do Socket.IO usado no Node.js.
 */
@Service
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Envia atualizacao de pedido para todos os clientes inscritos
     * no topico "/topic/orders", assim como um "emit" no Socket.IO.
     */
    public void notifyOrderUpdate(Order order) {
        messagingTemplate.convertAndSend("/topic/orders", order);
    }
}
