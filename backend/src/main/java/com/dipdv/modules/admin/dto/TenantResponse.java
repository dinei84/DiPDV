package com.dipdv.modules.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        List<String> enabledModules
) {}
