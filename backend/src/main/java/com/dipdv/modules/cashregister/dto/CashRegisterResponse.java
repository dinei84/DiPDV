package com.dipdv.modules.cashregister.dto;

import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CashRegisterResponse(
        UUID id,
        CashRegisterStatus status,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal physicalBalance,
        BigDecimal difference,
        BigDecimal totalCash,
        BigDecimal totalPix,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        List<CashMovementResponse> movements
) {}
