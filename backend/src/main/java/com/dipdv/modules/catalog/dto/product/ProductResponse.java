package com.dipdv.modules.catalog.dto.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Integer stockMinLevel,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
