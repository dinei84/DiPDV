package com.dipdv.modules.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemModifierResponse(
        UUID id,
        UUID modifierOptionId,
        String name,
        BigDecimal priceAddition,
        Integer quantity
) {}
