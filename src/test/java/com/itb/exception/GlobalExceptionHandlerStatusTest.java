package com.itb.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerStatusTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void smtpUnconfiguredKeepsServiceUnavailableStatus() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "A recuperação por e-mail não está configurada. Contate o administrador."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("não está configurada"));
    }

    @Test
    void expiredResetLinkKeepsBadRequestStatus() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link inválido, expirado ou já utilizado. Solicite outro."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
    }
}
