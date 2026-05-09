package com.dipdv.modules.catalog.dto.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String name,
        String description,
        BigDecimal price,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
