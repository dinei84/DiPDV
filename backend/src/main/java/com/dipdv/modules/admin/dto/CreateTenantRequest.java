package com.dipdv.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 60)  String slug,
        @NotBlank @Email           String ownerEmail,
        @NotBlank                  String ownerName,
        @NotBlank                  String ownerPassword
) {}
