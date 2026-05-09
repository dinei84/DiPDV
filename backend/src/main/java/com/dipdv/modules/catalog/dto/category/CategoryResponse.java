package com.dipdv.modules.catalog.dto.category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String icon,
        Boolean isDefault,
        Integer position,
        Long productCount,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt
) {
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
