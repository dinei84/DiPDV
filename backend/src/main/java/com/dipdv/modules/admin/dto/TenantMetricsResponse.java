package com.dipdv.modules.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantMetricsResponse(
        UUID id,
        String name,
        String planType,
        OffsetDateTime lastActivityAt,
        long orders30d,
        double revenue30d,
        String engagementStatus
) {}
