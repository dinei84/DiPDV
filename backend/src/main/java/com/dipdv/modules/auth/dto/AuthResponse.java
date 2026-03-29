package com.dipdv.modules.auth.dto;

import com.dipdv.modules.auth.entity.enums.UserRole;

import java.util.UUID;

/**
 * Resposta do login bem-sucedido.
 * Retorna o token JWT e os dados básicos do usuário autenticado
 * para o frontend popular o contexto de sessão.
 */
public record AuthResponse(
    String token,
    String tokenType,
    long expiresIn,
    UUID userId,
    UUID tenantId,
    String name,
    UserRole role
) {
    /** Factory method — deixa o Service mais legível */
    public static AuthResponse of(
            String token,
            long expiresInMs,
            UUID userId,
            UUID tenantId,
            String name,
            UserRole role) {
        return new AuthResponse(
            token,
            "Bearer",
            expiresInMs / 1000,   // converter ms → segundos para o frontend
            userId,
            tenantId,
            name,
            role
        );
    }
}
