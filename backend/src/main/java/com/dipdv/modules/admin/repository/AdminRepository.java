package com.dipdv.modules.admin.repository;

import com.dipdv.modules.admin.dto.GlobalStatsResponse;
import com.dipdv.modules.admin.dto.TenantListResponse;
import com.dipdv.modules.admin.dto.TenantMetricsResponse;
import com.dipdv.modules.admin.dto.TenantSummaryResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminRepository {

    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<TenantListResponse> listAllTenants() {
        return entityManager.createNativeQuery("""
                    SELECT
                        t.id,
                        t.name,
                        t.slug,
                        t.owner_email,
                        t.plan_type,
                        t.active,
                        t.last_activity_at,
                        t.created_at,
                        COUNT(DISTINCT u.id) AS user_count
                    FROM tenants t
                    LEFT JOIN users u ON u.tenant_id = t.id
                                      AND u.deleted_at IS NULL
                    WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
                    GROUP BY t.id
                    ORDER BY t.created_at DESC
                """)
                .getResultList()
                .stream()
                .map(row -> {
                    Object[] r = (Object[]) row;
                    return new TenantListResponse(
                            UUID.fromString(r[0].toString()),
                            r[1].toString(),
                            r[2] != null ? r[2].toString() : null,
                            r[3] != null ? r[3].toString() : null,
                            r[4].toString(),
                            (Boolean) r[5],
                            r[6] != null ? ((java.sql.Timestamp) r[6]).toInstant()
                                    .atOffset(java.time.ZoneOffset.UTC) : null,
                            ((java.sql.Timestamp) r[7]).toInstant()
                                    .atOffset(java.time.ZoneOffset.UTC),
                            ((Number) r[8]).longValue()
                    );
                })
                .toList();
    }

    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        Object[] tenant = (Object[]) entityManager.createNativeQuery("""
                    SELECT
                        t.id, t.name, t.slug, t.owner_email,
                        t.plan_type, t.active, t.last_activity_at, t.created_at,
                        COUNT(DISTINCT u.id)  AS user_count,
                        COUNT(DISTINCT p.id)  AS product_count,
                        COUNT(DISTINCT o.id)  AS order_count,
                        COALESCE(SUM(pay.amount), 0) AS total_revenue
                    FROM tenants t
                    LEFT JOIN users u     ON u.tenant_id = t.id AND u.deleted_at IS NULL
                    LEFT JOIN products p  ON p.tenant_id = t.id AND p.deleted_at IS NULL
                    LEFT JOIN orders o    ON o.tenant_id = t.id AND o.status = 'CLOSED'
                    LEFT JOIN payments pay ON pay.order_id = o.id AND pay.status = 'PAID'
                    WHERE t.id = :tenantId
                    GROUP BY t.id
                """)
                .setParameter("tenantId", tenantId)
                .getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> recentAudit = entityManager.createNativeQuery("""
                    SELECT action, entity, entity_id, created_at
                    FROM audit_log
                    WHERE tenant_id = :tenantId
                      AND is_admin_action = FALSE
                    ORDER BY created_at DESC
                    LIMIT 10
                """)
                .setParameter("tenantId", tenantId)
                .getResultList();

        return TenantSummaryResponse.from(tenant, recentAudit);
    }

    @SuppressWarnings("unchecked")
    public GlobalStatsResponse getGlobalStats() {
        Object[] totals = (Object[]) entityManager.createNativeQuery("""
                    SELECT
                        COUNT(DISTINCT t.id)                                AS tenant_count,
                        COUNT(DISTINCT CASE WHEN t.active THEN t.id END)    AS active_tenant_count,
                        COUNT(DISTINCT o.id)                                AS total_orders,
                        COALESCE(SUM(p.amount), 0)                          AS total_revenue
                    FROM tenants t
                    LEFT JOIN orders o   ON o.tenant_id = t.id AND o.status = 'CLOSED'
                    LEFT JOIN payments p ON p.order_id = o.id  AND p.status = 'PAID'
                    WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
                """)
                .getSingleResult();

        List<Object[]> byTenant = entityManager.createNativeQuery("""
                    SELECT
                        t.id, t.name, t.plan_type,
                        COUNT(DISTINCT o.id)       AS orders_30d,
                        COALESCE(SUM(p.amount), 0) AS revenue_30d
                    FROM tenants t
                    LEFT JOIN orders o   ON o.tenant_id = t.id
                                         AND o.status = 'CLOSED'
                                         AND o.closed_at >= NOW() - INTERVAL '30 days'
                    LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'PAID'
                    WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
                    GROUP BY t.id
                    ORDER BY revenue_30d DESC
                    LIMIT 10
                """)
                .getResultList();

        return GlobalStatsResponse.from(totals, byTenant);
    }

    @SuppressWarnings("unchecked")
    public List<TenantMetricsResponse> getEngagementMetrics() {
        return entityManager.createNativeQuery("""
                    SELECT
                        t.id, t.name, t.plan_type, t.last_activity_at,
                        COUNT(DISTINCT o.id)       AS orders_30d,
                        COALESCE(SUM(p.amount), 0) AS revenue_30d,
                        CASE
                            WHEN t.last_activity_at IS NULL THEN 'NEVER'
                            WHEN t.last_activity_at < NOW() - INTERVAL '30 days' THEN 'INACTIVE'
                            WHEN t.last_activity_at < NOW() - INTERVAL '7 days'  THEN 'AT_RISK'
                            ELSE 'ACTIVE'
                        END AS engagement_status
                    FROM tenants t
                    LEFT JOIN orders o   ON o.tenant_id = t.id
                                         AND o.closed_at >= NOW() - INTERVAL '30 days'
                    LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'PAID'
                    WHERE t.id != 'ffffffff-ffff-ffff-ffff-ffffffffffff'
                    GROUP BY t.id
                    ORDER BY orders_30d DESC
                """)
                .getResultList()
                .stream()
                .map(row -> {
                    Object[] r = (Object[]) row;
                    return new TenantMetricsResponse(
                            UUID.fromString(r[0].toString()),
                            r[1].toString(),
                            r[2].toString(),
                            r[3] != null ? ((java.sql.Timestamp) r[3]).toInstant()
                                    .atOffset(java.time.ZoneOffset.UTC) : null,
                            ((Number) r[4]).longValue(),
                            ((Number) r[5]).doubleValue(),
                            r[6].toString()
                    );
                })
                .toList();
    }
}
