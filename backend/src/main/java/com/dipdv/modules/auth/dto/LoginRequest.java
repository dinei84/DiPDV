package com.dipdv.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de login.
 * A partir da Sprint 6.1, o login é feito apenas com email e senha,
 * pois o email é globalmente único entre usuários ativos.
 */
public record LoginRequest(
    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    String email,

    @NotBlank(message = "senha é obrigatória")
    String password
) {}
