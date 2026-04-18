package com.dipdv.modules.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantListResponse(
        UUID id,
        String name,
        String slug,
        String ownerEmail,
        String planType,
        boolean active,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt,
        long userCount
) {}
