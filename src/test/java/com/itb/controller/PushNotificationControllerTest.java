package com.itb.controller;

import com.itb.dto.PushSubscriptionRequest;
import com.itb.service.OrderAccessService;
import com.itb.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushNotificationControllerTest {
    @Test
    void unauthorizedOrderDoesNotRegisterSubscription() {
        PushNotificationService push = mock(PushNotificationService.class);
        OrderAccessService access = mock(OrderAccessService.class);
        PushNotificationController controller = new PushNotificationController(push, access);
        PushSubscriptionRequest request = new PushSubscriptionRequest(12L,
                "https://fcm.googleapis.com/example", new PushSubscriptionRequest.Keys("key", "auth"));
        when(access.requireAccess(12L, "wrong-token")).thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class, () -> controller.subscribe(request, "wrong-token"));
        verifyNoInteractions(push);
    }

    @Test
    void authorizedGuestRegistrationChecksOrderBeforeWriting() {
        PushNotificationService push = mock(PushNotificationService.class);
        OrderAccessService access = mock(OrderAccessService.class);
        PushNotificationController controller = new PushNotificationController(push, access);
        PushSubscriptionRequest request = new PushSubscriptionRequest(12L,
                "https://fcm.googleapis.com/example", new PushSubscriptionRequest.Keys("key", "auth"));

        assertEquals(204, controller.subscribe(request, "guest-token").getStatusCode().value());
        var ordered = inOrder(access, push);
        ordered.verify(access).requireAccess(12L, "guest-token");
        ordered.verify(push).register(request);
    }
}
