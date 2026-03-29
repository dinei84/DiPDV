package com.dipdv.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload de login.
 * O tenantId é enviado pelo frontend junto com email e senha.
 * O frontend conhece o tenantId do tenant configurado na instalação.
 *
 * Usando Java record — imutável por padrão, sem boilerplate.
 */
public record LoginRequest(

    @NotNull(message = "tenantId é obrigatório")
    UUID tenantId,

    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    String email,

    @NotBlank(message = "senha é obrigatória")
    String password
) {}
