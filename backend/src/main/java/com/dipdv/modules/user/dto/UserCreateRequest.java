package com.dipdv.modules.user.dto;

import com.dipdv.modules.auth.entity.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequest(
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "nome é obrigatório")
        String name,

        @NotNull(message = "role é obrigatório")
        UserRole role,

        @NotBlank(message = "senha é obrigatória")
        String password
) {}
