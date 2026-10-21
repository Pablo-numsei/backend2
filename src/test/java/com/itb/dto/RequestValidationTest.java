package com.itb.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test void nullOrderItemIsInvalid() {
        assertFalse(VALIDATOR.validate(new PedidoCreateRequest(1L, null, Arrays.asList((ItemPedidoRequest) null))).isEmpty());
    }
    @Test void emptyOrderIsInvalid() {
        assertFalse(VALIDATOR.validate(new PedidoCreateRequest(1L, null, List.of())).isEmpty());
    }
    @Test void tooManyOrderItemsIsInvalid() {
        assertFalse(VALIDATOR.validate(new PedidoCreateRequest(1L, null,
                Collections.nCopies(51, new ItemPedidoRequest(1L, 1)))).isEmpty());
    }
    @Test void quantityIsBounded() {
        assertFalse(VALIDATOR.validate(new ItemPedidoRequest(1L, 101)).isEmpty());
        assertFalse(VALIDATOR.validate(new ItemPedidoRequest(1L, 0)).isEmpty());
        assertTrue(VALIDATOR.validate(new ItemPedidoRequest(1L, 100)).isEmpty());
    }
    @Test void tableAndProductIdsArePositive() {
        assertFalse(VALIDATOR.validate(new ItemPedidoRequest(-1L, 1)).isEmpty());
        assertFalse(VALIDATOR.validate(new PedidoCreateRequest(0L, null, List.of(new ItemPedidoRequest(1L, 1)))).isEmpty());
    }
    @Test void serviceRequestDetailsHaveSchemaLimit() {
        assertFalse(VALIDATOR.validate(new AtendimentoCreateRequest(1L, null, "GARCOM", "x".repeat(256))).isEmpty());
        assertTrue(VALIDATOR.validate(new AtendimentoCreateRequest(1L, null, "GARCOM", "x".repeat(255))).isEmpty());
    }
    @Test void orderNotesHaveMaximumLengthButRemainOptional() {
        var items = List.of(new ItemPedidoRequest(8L, 1));
        assertTrue(VALIDATOR.validate(new PedidoCreateRequest(1L, null, items, null, null, null)).isEmpty());
        assertTrue(VALIDATOR.validate(new PedidoCreateRequest(1L, null, items, null, null,
                "carne bem passada")).isEmpty());
        assertFalse(VALIDATOR.validate(new PedidoCreateRequest(1L, null, items, null, null,
                "x".repeat(256))).isEmpty());
    }
    @Test void newPasswordsNeedTwelveCharacters() {
        assertFalse(VALIDATOR.validate(new UsuarioCreateRequest(1L, "Test", "test@example.invalid", "shortpass")).isEmpty());
        assertTrue(VALIDATOR.validate(new UsuarioCreateRequest(1L, "Test", "test@example.invalid", "test-password-12")).isEmpty());
    }
}
