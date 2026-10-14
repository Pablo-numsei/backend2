package com.itb.service;

import com.itb.model.Perfil;
import com.itb.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    // Chave sintética exclusiva dos testes; nenhuma configuração de ambiente é lida.
    private static final byte[] TEST_KEY = "tablehub-unit-tests-key-32-bytes!".getBytes(StandardCharsets.UTF_8);
    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", Base64.getEncoder().encodeToString(TEST_KEY));
        ReflectionTestUtils.setField(jwtService, "expiration", 60000L);
        ReflectionTestUtils.setField(jwtService, "orderExpiration", 60000L);
    }

    @Test
    void generatedStaffTokenContainsIdentityAndValidStaffPurpose() {
        String token = jwtService.generateToken(staff());
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(TEST_KEY)).build()
                .parseSignedClaims(token).getPayload();

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("staff", claims.get("purpose", String.class));
        assertEquals("admin@tablehub.test", jwtService.extractEmail(token));
        assertEquals(7L, claims.get("usuarioId", Long.class));
        assertEquals("ADMINISTRADOR", claims.get("perfil", String.class));
    }

    @Test
    void orderCapabilityCannotAuthenticateAsStaff() {
        String token = jwtService.generateOrderToken(41L);

        assertTrue(jwtService.isOrderTokenValid(token, 41L));
        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void staffTokenCannotActAsOrderCapability() {
        assertFalse(jwtService.isOrderTokenValid(jwtService.generateToken(staff()), 41L));
    }

    @Test
    void orderCapabilityCannotReadAnotherOrder() {
        String token = jwtService.generateOrderToken(41L);

        assertFalse(jwtService.isOrderTokenValid(token, 42L));
        assertFalse(jwtService.isOrderTokenValid(token, null));
    }

    @Test
    void changingStaffSignatureInvalidatesAuthentication() {
        assertFalse(jwtService.isTokenValid(tamperSignature(jwtService.generateToken(staff()))));
    }

    @Test
    void changingOrderSignatureInvalidatesCapability() {
        assertFalse(jwtService.isOrderTokenValid(tamperSignature(jwtService.generateOrderToken(41L)), 41L));
    }

    @Test
    void expiredStaffTokenCannotAuthenticate() {
        ReflectionTestUtils.setField(jwtService, "expiration", -60000L);

        assertFalse(jwtService.isTokenValid(jwtService.generateToken(staff())));
    }

    @Test
    void expiredOrderTokenCannotReadOrder() {
        ReflectionTestUtils.setField(jwtService, "orderExpiration", -60000L);

        assertFalse(jwtService.isOrderTokenValid(jwtService.generateOrderToken(41L), 41L));
    }

    @Test
    void validSignatureWithUnknownPurposeIsRejected() {
        String token = Jwts.builder().subject("admin@tablehub.test")
                .claim("purpose", "password-reset").claim("orderId", 41L)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact();

        assertFalse(jwtService.isTokenValid(token));
        assertFalse(jwtService.isOrderTokenValid(token, 41L));
    }

    @Test
    void tokenSignedWithDifferentKeyCannotAuthenticate() {
        byte[] otherKey = "another-unit-test-key-with-32-bytes".getBytes(StandardCharsets.UTF_8);
        String token = Jwts.builder().subject("admin@tablehub.test").claim("purpose", "staff")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(otherKey)).compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void unsignedTokenCannotAssertStaffOrOrderPurpose() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"purpose\":\"staff\",\"orderId\":41,\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + payload + ".";

        assertFalse(jwtService.isTokenValid(unsignedToken));
        assertFalse(jwtService.isOrderTokenValid(unsignedToken, 41L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-token", "abc.def.ghi"})
    void malformedTokensFailClosed(String token) {
        assertFalse(jwtService.isTokenValid(token));
        assertFalse(jwtService.isOrderTokenValid(token, 41L));
    }

    private Usuario staff() {
        Perfil perfil = new Perfil();
        perfil.setId(1L);
        perfil.setName("ADMINISTRADOR");
        Usuario user = new Usuario();
        user.setId(7L);
        user.setEmail("admin@tablehub.test");
        user.setPerfil(perfil);
        return user;
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
        return String.join(".", parts);
    }
}
