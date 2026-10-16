package com.itb.controller;

import com.itb.dto.PasswordResetRequest;
import com.itb.dto.PasswordResetConfirmRequest;
import com.itb.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService reset;

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequest body) {
        reset.request(body.email());
        return ResponseEntity.accepted().body(Map.of("message",
                "Se o e-mail estiver cadastrado e ativo, você receberá as instruções de recuperação."));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@Valid @RequestBody PasswordResetConfirmRequest body) {
        reset.confirm(body.token(), body.novaSenha());
        return ResponseEntity.ok(Map.of("message", "Senha alterada. Entre novamente no TableHub."));
    }
}
