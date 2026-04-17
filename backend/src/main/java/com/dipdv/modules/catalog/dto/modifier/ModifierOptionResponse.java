package com.dipdv.modules.catalog.dto.modifier;

import java.math.BigDecimal;
import java.util.UUID;

public record ModifierOptionResponse(
    UUID id,
    String name,
    BigDecimal priceAddition,
    Integer maxQuantity,
    Integer position,
    Boolean active
) {
}