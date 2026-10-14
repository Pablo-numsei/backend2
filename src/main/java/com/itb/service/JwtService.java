package com.itb.service;

import com.itb.model.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.order-expiration:86400000}")
    private long orderExpiration;

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

    private Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
