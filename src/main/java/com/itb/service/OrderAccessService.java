package com.itb.service;

import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Order;
import com.itb.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderAccessService {
    private final OrderRepository orderRepository;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    /**
     * Acesso financeiro exige perfil ADMINISTRADOR/GARCOM ou token próprio do pedido.
     * Um funcionário COZINHA não pode usar o token de cliente para contornar o RBAC.
     */
    public Order requirePaymentAccess(Long orderId, String orderToken) {
        if (currentUser.isStaff() && !currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM")) {
            throw new AccessDeniedException("Seu perfil não pode acessar pagamentos.");
        }
        return requireAccess(orderId, orderToken);
    }

    public Order requireAccess(Long orderId, String orderToken) {
        // Check capability first, so unauthorised visitors cannot enumerate order IDs.
        if (!currentUser.isStaff() && !jwtService.isOrderTokenValid(orderToken, orderId)) {
            throw new AccessDeniedException("Acesso não permitido a este pedido.");
        }
        return orderRepository.findById(orderId).filter(order -> order.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
    }
}
