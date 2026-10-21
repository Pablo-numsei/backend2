package com.itb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** Keep weak four-digit PINs protected with HMAC and a slow password hash. */
@Service
public class ClientePinService {
    private final PasswordEncoder encoder;
    private final byte[] pepper;

    public ClientePinService(PasswordEncoder encoder,
            @Value("${customer.pin.pepper:${jwt.secret}}") String pepper) {
        if (pepper == null || pepper.length() < 32)
            throw new IllegalStateException("Configure um segredo de PIN forte no backend.");
        this.encoder = encoder;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public static String validate(String pin) {
        if (pin == null || !pin.matches("[0-9]{4}"))
            throw new IllegalArgumentException("O PIN precisa ter exatamente 4 dígitos.");
        return pin;
    }

    private String digest(String username, String pin) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] digest = hmac.doFinal(
                    (username.trim().toLowerCase(Locale.ROOT) + ":" + validate(pin))
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Proteção de PIN indisponível.", error);
        }
    }

    public String encode(String username, String pin) {
        return encoder.encode(digest(username, pin));
    }

    public boolean matches(String username, String pin, String pinHash) {
        return pinHash != null && !pinHash.isBlank()
                && encoder.matches(digest(username, pin), pinHash);
    }
}
