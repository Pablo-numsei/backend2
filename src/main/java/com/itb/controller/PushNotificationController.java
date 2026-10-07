package com.itb.controller;

import com.itb.dto.PushSubscriptionRequest;
import com.itb.service.PushNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    public PushNotificationController(
            PushNotificationService pushNotificationService
    ) {
        this.pushNotificationService = pushNotificationService;
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
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        pushNotificationService.register(request);
        return ResponseEntity.noContent().build();
    }
}
