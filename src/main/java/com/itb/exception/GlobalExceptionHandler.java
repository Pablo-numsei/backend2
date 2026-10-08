package com.itb.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - recurso não encontrado
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
    }

    // 400 - dados inválidos
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
    }

    // 409 - operação inválida para o estado atual
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            IllegalStateException ex
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
    }

    // 409 - conflito com constraint do banco
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConflict(
            DataIntegrityViolationException ex
    ) {
        log.warn("Conflito de integridade no banco de dados.", ex);

        return buildResponse(
                HttpStatus.CONFLICT,
                "A operação viola uma regra de integridade do banco de dados."
        );
    }

    // 400 - erros das anotações @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Dados inválidos.");

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // 500 - erro inesperado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception ex
    ) {
        log.error(
                "Erro inesperado tratado pelo GlobalExceptionHandler: {}",
                ex.getMessage(),
                ex
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno no servidor."
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String message
    ) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return ResponseEntity
                .status(status)
                .body(body);
    }
}
