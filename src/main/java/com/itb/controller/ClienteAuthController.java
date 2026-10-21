package com.itb.controller;

import com.itb.dto.ClienteAuthResponse;
import com.itb.dto.ClienteLoginRequest;
import com.itb.dto.ClienteProfileResponse;
import com.itb.service.ClienteAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteAuthController {
    private final ClienteAuthService auth;

    @PostMapping("/login")
    public ResponseEntity<ClienteAuthResponse> login(@Valid @RequestBody ClienteLoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<ClienteProfileResponse> profile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(auth.profile(authorization));
    }
}
