package com.dipdv.modules.report.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CashRegisterReportResponse(
        String operatorName,
        double openingBalance,
        double closingBalance,
        double physicalBalance,
        double difference,
        double totalCash,
        double totalPix,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        List<MovementItem> movements
) {
    public record MovementItem(
            String type,
            double amount,
            String description,
            OffsetDateTime createdAt
    ) {}

    public static CashRegisterReportResponse from(Object[] cr, List<Object[]> movements) {
        List<MovementItem> movementItems = movements.stream()
                .map(row -> new MovementItem(
                        row[0].toString(),
                        ((Number) row[1]).doubleValue(),
                        row[2] != null ? row[2].toString() : "",
                        row[3] != null ? (OffsetDateTime) row[3] : null
                ))
                .toList();

        return new CashRegisterReportResponse(
                cr[8] != null ? cr[8].toString() : "",
                ((Number) cr[0]).doubleValue(),
                ((Number) cr[1]).doubleValue(),
                ((Number) cr[2]).doubleValue(),
                ((Number) cr[3]).doubleValue(),
                ((Number) cr[4]).doubleValue(),
                ((Number) cr[5]).doubleValue(),
                cr[6] != null ? (OffsetDateTime) cr[6] : null,
                cr[7] != null ? (OffsetDateTime) cr[7] : null,
                movementItems
        );
    }
}
