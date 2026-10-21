package com.itb.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class ClientePinServiceTest {
    private final ClientePinService pins = new ClientePinService(
            new BCryptPasswordEncoder(4),
            "synthetic-test-only-pepper-at-least-32-characters");

    @Test
    void preservesLeadingZerosAndNeverStoresPlaintextPin() {
        String hash = pins.encode("pablo123", "0472");
        assertNotEquals("0472", hash);
        assertFalse(hash.contains("0472"));
        assertTrue(pins.matches("PABLO123", "0472", hash));
        assertFalse(pins.matches("pablo123", "0000", hash));
        assertFalse(pins.matches("outroUsuario", "0472", hash));
    }

    @Test
    void refusesAllNonFourDigitValues() {
        for (String value : new String[] {"123", "12345", "12a4", "1 24", "-123", ""}) {
            assertThrows(IllegalArgumentException.class, () -> pins.encode("pablo123", value));
        }
    }
}
