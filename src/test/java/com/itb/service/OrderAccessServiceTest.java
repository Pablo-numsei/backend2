package com.itb.service;

import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Order;
import com.itb.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAccessServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private JwtService jwtService;
    @Mock private CurrentUserService currentUser;
    @InjectMocks private OrderAccessService orderAccessService;

    private Order order;

    @Test
    void kitchenCannotAccessPaymentsEvenWithCustomerCapability() {
        when(currentUser.isStaff()).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> orderAccessService.requirePaymentAccess(41L, "order-41-token"));

        verify(currentUser).hasAnyRole("ADMINISTRADOR", "GARCOM");
        verifyNoInteractions(orderRepository, jwtService);
    }

    @Test
    void authorizedOperationalStaffCanAccessPaymentWithoutOrderToken() {
        when(currentUser.isStaff()).thenReturn(true);
        when(currentUser.hasAnyRole("ADMINISTRADOR", "GARCOM")).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertSame(order, orderAccessService.requirePaymentAccess(41L, null));

        verifyNoInteractions(jwtService);
    }

    @Test
    void anonymousCustomerCanAccessPaymentOnlyWithMatchingOrderCapability() {
        when(jwtService.isOrderTokenValid("order-41-token", 41L)).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertSame(order, orderAccessService.requirePaymentAccess(41L, "order-41-token"));
        assertThrows(AccessDeniedException.class,
                () -> orderAccessService.requirePaymentAccess(42L, "order-41-token"));
        verify(orderRepository, never()).findById(42L);
    }

    @Test
    void anonymousCustomerWithoutOrderCapabilityCannotAccessPayments() {
        assertThrows(AccessDeniedException.class,
                () -> orderAccessService.requirePaymentAccess(41L, null));

        verifyNoInteractions(orderRepository);
    }

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(41L);
    }

    @Test
    void absentCapabilityIsRejectedBeforeLookingUpOrder() {
        assertThrows(AccessDeniedException.class, () -> orderAccessService.requireAccess(41L, null));

        verifyNoInteractions(orderRepository);
        verify(jwtService).isOrderTokenValid(null, 41L);
    }

    @Test
    void invalidCapabilityCannotEnumerateExistingOrMissingIds() {
        assertThrows(AccessDeniedException.class, () -> orderAccessService.requireAccess(41L, "invalid"));
        assertThrows(AccessDeniedException.class, () -> orderAccessService.requireAccess(999L, "invalid"));

        verifyNoInteractions(orderRepository);
    }

    @Test
    void validCapabilityReadsOnlyItsAuthorizedOrder() {
        when(jwtService.isOrderTokenValid("order-41-token", 41L)).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertSame(order, orderAccessService.requireAccess(41L, "order-41-token"));
        assertThrows(AccessDeniedException.class,
                () -> orderAccessService.requireAccess(42L, "order-41-token"));
        verify(orderRepository, never()).findById(42L);
    }

    @Test
    void staffCanReadOrderWithoutClientCapability() {
        when(currentUser.isStaff()).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertSame(order, orderAccessService.requireAccess(41L, null));

        verifyNoInteractions(jwtService);
    }

    @Test
    void validCapabilityCannotReadDeletedOrder() {
        order.setDeletedAt(LocalDateTime.now());
        when(jwtService.isOrderTokenValid("order-41-token", 41L)).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class,
                () -> orderAccessService.requireAccess(41L, "order-41-token"));
    }

    @Test
    void staffAlsoCannotReadDeletedOrder() {
        order.setDeletedAt(LocalDateTime.now());
        when(currentUser.isStaff()).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class, () -> orderAccessService.requireAccess(41L, null));
    }

    @Test
    void authorizedReadOfMissingOrderReturnsNotFound() {
        when(jwtService.isOrderTokenValid("order-41-token", 41L)).thenReturn(true);
        when(orderRepository.findById(41L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderAccessService.requireAccess(41L, "order-41-token"));
    }
}
