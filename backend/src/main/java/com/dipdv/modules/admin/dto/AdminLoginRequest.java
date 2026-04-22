package com.dipdv.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "email Ã© obrigatÃ³rio")
        @Email(message = "email invÃ¡lido")
        String email,

        @NotBlank(message = "senha Ã© obrigatÃ³ria")
        String password
) {}
