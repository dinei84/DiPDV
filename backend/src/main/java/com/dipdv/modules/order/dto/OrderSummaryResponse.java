package com.dipdv.modules.order.dto;

import com.dipdv.modules.order.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        long itemCount,
        OffsetDateTime createdAt
) {}
