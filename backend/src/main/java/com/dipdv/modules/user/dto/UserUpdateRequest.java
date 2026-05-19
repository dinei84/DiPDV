package com.dipdv.modules.user.dto;

import com.dipdv.modules.auth.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequest(
        @NotBlank(message = "nome é obrigatório")
        String name,

        @NotNull(message = "role é obrigatório")
        UserRole role,

        String password
) {}
