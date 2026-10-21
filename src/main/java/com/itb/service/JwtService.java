package com.itb.service;

import com.itb.model.Usuario;
import com.itb.model.Cliente;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.order-expiration:86400000}")
    private long orderExpiration;

    @Value("${jwt.customer-expiration:3600000}")
    private long customerExpiration;

    private static final long RESET_TTL_MS = 15 * 60 * 1000L;

    /** Recovery links are signed, scoped to this purpose and invalidated when the password hash changes. */
    public String generatePasswordResetToken(Usuario usuario) {
        Date now = new Date();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("purpose", "password-reset")
                .claim("usuarioId", usuario.getId())
                .claim("passwordVersion", passwordVersion(usuario.getPasswordHash()))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + RESET_TTL_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public PasswordResetClaims readPasswordResetToken(String token) {
        if (token == null || token.isBlank() || token.length() > 2048) return null;
        try {
            Claims claims = extractClaims(token);
            Long id = claims.get("usuarioId", Long.class);
            String version = claims.get("passwordVersion", String.class);
            if (!"password-reset".equals(claims.get("purpose", String.class))
                    || id == null || id <= 0 || version == null || version.isBlank()
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getExpiration() == null || !claims.getExpiration().after(new Date())) return null;
            return new PasswordResetClaims(id, claims.getSubject(), version);
        } catch (Exception invalid) {
            return null;
        }
    }

    public static String passwordVersion(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Hash ausente.");
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    public record PasswordResetClaims(Long usuarioId, String email, String passwordVersion) {}

    private SecretKey getSigningKey() {

        byte[] keyBytes =
                Decoders.BASE64.decode(secret);

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Usuario usuario) {

        Date now = new Date();

        Date expirationDate =
                new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim("purpose", "staff")
                .subject(usuario.getEmail())
                .claim(
                        "usuarioId",
                        usuario.getId()
                )
                .claim(
                        "perfil",
                        usuario.getPerfil().getName()
                )
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {

        return extractClaims(token)
                .getSubject();
    }

    public boolean isTokenValid(String token) {

        try {

            Claims claims =
                    extractClaims(token);

            return "staff".equals(claims.get("purpose", String.class)) && claims.getExpiration()
                    .after(new Date());

        } catch (Exception ex) {

            return false;
        }
    }

    /** Customer credentials are a separate JWT purpose, never accepted as staff access. */
    public String generateCustomerToken(Cliente cliente) {
        Date now = new Date();
        return Jwts.builder()
                .subject(cliente.getUsername())
                .claim("purpose", "customer")
                .claim("clienteId", cliente.getId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + customerExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** Returns null for staff, order, reset or expired tokens. */
    public CustomerTokenClaims readCustomerToken(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) return null;
        try {
            Claims claims = extractClaims(token);
            Long id = claims.get("clienteId", Long.class);
            if (!"customer".equals(claims.get("purpose", String.class))
                    || id == null || id <= 0
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getExpiration() == null || !claims.getExpiration().after(new Date())) return null;
            return new CustomerTokenClaims(id, claims.getSubject());
        } catch (Exception invalidToken) { return null; }
    }

    public record CustomerTokenClaims(Long clienteId, String username) {}

    public String generateOrderToken(Long orderId) {
        Date now = new Date();
        return Jwts.builder().subject("order:" + orderId)
                .claim("purpose", "order-access").claim("orderId", orderId)
                .issuedAt(now).expiration(new Date(now.getTime() + orderExpiration))
                .signWith(getSigningKey()).compact();
    }

    public boolean isOrderTokenValid(String token, Long orderId) {
        if (token == null || token.isBlank() || orderId == null) return false;
        try {
            Claims claims = extractClaims(token);
            return "order-access".equals(claims.get("purpose", String.class))
                    && orderId.equals(claims.get("orderId", Long.class))
                    && claims.getExpiration().after(new Date());
        } catch (Exception invalidToken) { return false; }
    }

    /** QR imprimível: a validade é controlada pela versão e pelo estado atual da mesa. */
    public String generateTableQrToken(Long mesaId, String qrVersion) {
        return Jwts.builder().subject("table:" + mesaId)
                .claim("purpose", "table-qr").claim("mesaId", mesaId).claim("qrVersion", qrVersion)
                .signWith(getSigningKey()).compact();
    }

    public String generateTableAccessToken(Long mesaId, String qrVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder().subject("table:" + mesaId)
                .claim("purpose", "table-access").claim("mesaId", mesaId).claim("qrVersion", qrVersion)
                .issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
                .signWith(getSigningKey()).compact();
    }

    public TableTokenClaims readTableQrToken(String token) {
        return readTableToken(token, "table-qr", false);
    }

    public TableTokenClaims readTableAccessToken(String token) {
        return readTableToken(token, "table-access", true);
    }

    private TableTokenClaims readTableToken(String token, String purpose, boolean requireExpiry) {
        if (token == null || token.isBlank() || token.length() > 4096) return null;
        try {
            Claims claims = extractClaims(token);
            Long mesaId = claims.get("mesaId", Long.class);
            String version = claims.get("qrVersion", String.class);
            Date expires = claims.getExpiration();
            if (!purpose.equals(claims.get("purpose", String.class)) || mesaId == null || mesaId <= 0
                    || !("table:" + mesaId).equals(claims.getSubject())
                    || version == null || version.isBlank() || version.length() > 128
                    || (requireExpiry && (expires == null || !expires.after(new Date())))) return null;
            return new TableTokenClaims(mesaId, version, expires == null ? null : expires.toInstant());
        } catch (Exception invalidToken) {
            return null;
        }
    }

    public record TableTokenClaims(Long mesaId, String qrVersion, Instant expiresAt) {}

    private Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
