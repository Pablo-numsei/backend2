package com.itb.controller;

import com.itb.dto.PushSubscriptionRequest;
import com.itb.dto.StaffPushSubscriptionRequest;
import com.itb.service.PushNotificationService;
import com.itb.service.OrderAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;
    private final OrderAccessService orderAccessService;

    public PushNotificationController(
            PushNotificationService pushNotificationService,
            OrderAccessService orderAccessService
    ) {
        this.pushNotificationService = pushNotificationService;
        this.orderAccessService = orderAccessService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<?> publicKey() {
        if (!pushNotificationService.isConfigured()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "message",
                            "As notificações push ainda não foram configuradas no servidor."
                    ));
        }

        return ResponseEntity.ok(
                Map.of("publicKey", pushNotificationService.getPublicKey())
        );
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = "X-Order-Token", required = false) String token
    ) {
        orderAccessService.requireAccess(request.pedidoId(), token);
        pushNotificationService.register(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff-subscriptions")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GARCOM')")
    public ResponseEntity<Void> subscribeStaff(
            @Valid @RequestBody StaffPushSubscriptionRequest request
    ) {
        pushNotificationService.registerStaff(request);
        return ResponseEntity.noContent().build();
    }
}
