package com.dipdv.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FirstAdminRequest(
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "nome é obrigatório")
        String name,

        @NotBlank(message = "senha é obrigatória")
        String password
) {}
