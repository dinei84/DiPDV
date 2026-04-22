package com.dipdv.modules.admin.dto;

import java.util.UUID;

public record AdminLoginResponse(
        UUID userId,
        String name,
        String role
) {}
