package com.itb.controller;

import com.itb.dto.AvancarStatusRequest;
import com.itb.dto.OrderResponse;
import com.itb.dto.PedidoCreateRequest;
import com.itb.model.Order;
import com.itb.model.Cliente;
import com.itb.repository.OrderRepository;
import com.itb.service.CurrentUserService;
import com.itb.service.ClienteAuthService;
import com.itb.service.JwtService;
import com.itb.service.OrderAccessService;
import com.itb.service.PedidoService;
import com.itb.service.TableAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class OrderController {
    private final OrderRepository orderRepository;
    private final PedidoService pedidoService;
    private final OrderAccessService orderAccessService;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    private final TableAccessService tableAccessService;
    private final ClienteAuthService clienteAuth;

    @GetMapping
    public List<OrderResponse> getAll() {
        return orderRepository.findByDeletedAtIsNullOrderByCreatedAtAsc().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id,
            @RequestHeader(value = "X-Order-Token", required = false) String token) {
        return OrderResponse.from(orderAccessService.requireAccess(id, token));
    }

    @GetMapping("/status/{status}")
    public List<OrderResponse> getByStatus(@PathVariable String status) {
        return orderRepository.findByStatus_NameAndDeletedAtIsNullOrderByCreatedAtAsc(status)
                .stream().map(OrderResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<OrderResponse> criarPedido(@Valid @RequestBody PedidoCreateRequest request,
            @RequestHeader(value = "X-Table-Token", required = false) String tableToken,
            @RequestHeader(value = "X-Customer-Token", required = false) String customerToken) {
        tableAccessService.requireWriteAccess(request.mesaId(), tableToken);
        Long staffId = currentUser.idOrNull();
        Cliente cliente = staffId == null ? clienteAuth.requireCustomer(customerToken) : null;
        // No forged staff id or customer id from the HTTP body is ever trusted.
        Order order = pedidoService.criarPedido(new PedidoCreateRequest(
                request.mesaId(), staffId, request.itens(),
                cliente == null ? null : cliente.getId(),
                cliente == null ? null : cliente.getUsername()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order, jwtService.generateOrderToken(order.getId())));
    }

    @PatchMapping("/{id}/status")
    public OrderResponse avancarStatus(@PathVariable Long id, @Valid @RequestBody AvancarStatusRequest request) {
        String status = request.novoStatus().trim();
        if (!currentUser.hasAnyRole("ADMINISTRADOR") &&
                !(currentUser.hasAnyRole("COZINHA") && (status.equals("Em preparo") || status.equals("Pronto"))) &&
                !(currentUser.hasAnyRole("GARCOM") && status.equals("Entregue"))) {
            throw new AccessDeniedException("Seu perfil não pode executar esta transição.");
        }
        return OrderResponse.from(pedidoService.avancarStatus(id, new AvancarStatusRequest(status, currentUser.idOrNull())));
    }
}
