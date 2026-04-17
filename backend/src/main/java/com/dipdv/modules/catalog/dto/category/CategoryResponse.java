package com.dipdv.modules.catalog.dto.category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        Integer position,
        Boolean active,
        OffsetDateTime createdAt
) {
}
