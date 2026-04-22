package com.dipdv.modules.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TenantSummaryResponse(
        UUID id,
        String name,
        String slug,
        String ownerEmail,
        String planType,
        boolean active,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt,
        long userCount,
        long productCount,
        long orderCount,
        double totalRevenue,
        List<AuditItem> recentAudit
) {

    public record AuditItem(
            String action,
            String entity,
            String entityId,
            OffsetDateTime createdAt
    ) {}

    public static TenantSummaryResponse from(Object[] row, List<Object[]> audit) {
        List<AuditItem> auditItems = audit.stream()
                .map(a -> new AuditItem(
                        a[0] != null ? a[0].toString() : null,
                        a[1] != null ? a[1].toString() : null,
                        a[2] != null ? a[2].toString() : null,
                        a[3] != null ? ((java.sql.Timestamp) a[3]).toInstant()
                                .atOffset(java.time.ZoneOffset.UTC) : null
                ))
                .toList();

        return new TenantSummaryResponse(
                UUID.fromString(row[0].toString()),
                row[1].toString(),
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4].toString(),
                (Boolean) row[5],
                row[6] != null ? ((java.sql.Timestamp) row[6]).toInstant()
                        .atOffset(java.time.ZoneOffset.UTC) : null,
                ((java.sql.Timestamp) row[7]).toInstant().atOffset(java.time.ZoneOffset.UTC),
                ((Number) row[8]).longValue(),
                ((Number) row[9]).longValue(),
                ((Number) row[10]).longValue(),
                ((Number) row[11]).doubleValue(),
                auditItems
        );
    }
}
