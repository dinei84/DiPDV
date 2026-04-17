package com.dipdv.modules.cashregister.dto;

import com.dipdv.modules.cashregister.entity.enums.CashMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        CashMovementType type,
        BigDecimal amount,
        String description,
        OffsetDateTime createdAt
) {}
