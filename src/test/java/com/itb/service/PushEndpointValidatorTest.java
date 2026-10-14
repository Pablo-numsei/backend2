package com.itb.service;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PushEndpointValidatorTest {
    private final PushEndpointValidator validator = new PushEndpointValidator(new String[] {
            "fcm.googleapis.com", "*.push.services.mozilla.com", "web.push.apple.com" });

    @Test
    void allowsRecognizedHttpsPushProviders() {
        assertNotNull(validator.validate("https://fcm.googleapis.com/fcm/send/example"));
        assertNotNull(validator.validate("https://updates.push.services.mozilla.com/wpush/v2/example"));
        assertNotNull(validator.validate("https://web.push.apple.com/example"));
    }

    @Test
    void rejectsInternalUrlsHostConfusionAndRedirectInputs() {
        for (String endpoint : new String[] {
                "http://fcm.googleapis.com/example", "https://127.0.0.1/", "https://[::1]/",
                "https://fcm.googleapis.com.evil.example/", "https://evil.example@fcm.googleapis.com/",
                "https://fcm.googleapis.com:8443/", "https://fcm.googleapis.com/#fragment",
                "https://evil-fcm.googleapis.com/", "https://push.services.mozilla.com.evil.example/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(endpoint), endpoint);
        }
    }

    @Test
    void rejectsPrivateAndSpecialAddressRangesWithoutNetworkCalls() throws Exception {
        for (byte[] address : new byte[][] {
                {127, 0, 0, 1}, {10, 0, 0, 1}, {(byte) 169, (byte) 254, 1, 1},
                {100, 64, 0, 1}, {(byte) 198, 18, 0, 1}, {0, 0, 0, 1},
                {(byte) 224, 0, 0, 1},
                {(byte) 0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}
        }) {
            assertFalse(PushEndpointValidator.isPublicAddress(InetAddress.getByAddress(address)));
        }
        assertTrue(PushEndpointValidator.isPublicAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8})));
    }

    @Test
    void refusesUnrestrictedConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PushEndpointValidator(new String[] {"*"}));
    }
}
