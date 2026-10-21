package com.itb.controller;

import com.itb.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WhatsAppStatusController {
    private final WhatsAppNotificationService whatsapp;

    /** Describes server configuration, not guaranteed delivery to a user's handset. */
    @GetMapping("/api/whatsapp/status")
    public Map<String, Boolean> status() {
        return Map.of("available", whatsapp.isConfigured());
    }
}
