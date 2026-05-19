package com.dipdv.modules.order.dto;

import com.dipdv.modules.order.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String identifier,
        OrderStatus status,
        BigDecimal total,
        UUID cashRegisterId,
        List<OrderItemResponse> items,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt
) {}
