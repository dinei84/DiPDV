package com.dipdv.modules.admin.dto;

import java.util.List;
import java.util.UUID;

public record GlobalStatsResponse(
        long tenantCount,
        long activeTenantCount,
        long totalOrders,
        double totalRevenue,
        List<TenantRankItem> topTenants
) {

    public record TenantRankItem(
            UUID id,
            String name,
            String planType,
            long orders30d,
            double revenue30d
    ) {}

    public static GlobalStatsResponse from(Object[] totals, List<Object[]> byTenant) {
        List<TenantRankItem> top = byTenant.stream()
                .map(r -> new TenantRankItem(
                        UUID.fromString(r[0].toString()),
                        r[1].toString(),
                        r[2].toString(),
                        ((Number) r[3]).longValue(),
                        ((Number) r[4]).doubleValue()
                ))
                .toList();

        return new GlobalStatsResponse(
                ((Number) totals[0]).longValue(),
                ((Number) totals[1]).longValue(),
                ((Number) totals[2]).longValue(),
                ((Number) totals[3]).doubleValue(),
                top
        );
    }
}
