package com.dipdv.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Serviço responsável por gerar e validar JWTs.
 *
 * ESTRUTURA DO TOKEN:
 * Header: { alg: HS256, typ: JWT }
 * Payload (claims):
 *   sub        → userId (UUID do usuário)
 *   tenantId   → UUID do tenant (resolve o isolamento multi-tenant)
 *   role       → ADMIN | MANAGER | CASHIER
 *   iat        → issued at (gerado automaticamente)
 *   exp        → expiration (configurável via application.yml)
 *
 * O tenantId no JWT evita qualquer consulta adicional ao banco
 * para identificar o contexto do request — extraído uma vez no
 * JwtAuthFilter e propagado via TenantContext para toda a thread.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${dipdv.jwt.secret}") String secret,
            @Value("${dipdv.jwt.expiration-ms}") long expirationMs) {

        // Mínimo de 256 bits para HMAC-SHA256
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                "JWT_SECRET deve ter no mínimo 32 caracteres (256 bits para HMAC-SHA256)"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Gera um JWT com os claims do usuário autenticado.
     *
     * @param userId   UUID do usuário
     * @param tenantId UUID do tenant ao qual o usuário pertence
     * @param role     Role do usuário: ADMIN, MANAGER ou CASHIER
     * @return token JWT assinado com HMAC-SHA256
     */
    public String generateToken(UUID userId, UUID tenantId, String role) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida o token e retorna os claims internos.
     * Lança JwtException se o token for inválido ou expirado.
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            // Relança como JwtException para o JwtAuthFilter tratar como 401
            throw new JwtException("Token JWT inválido ou expirado: " + e.getMessage(), e);
        }
    }

    // ── Helpers de extração ──────────────────────────────────────────────────

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractTenantId(Claims claims) {
        return UUID.fromString(claims.get("tenantId", String.class));
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
