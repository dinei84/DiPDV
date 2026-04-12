package com.dipdv.modules.report.repository;

import com.dipdv.modules.report.dto.CashRegisterReportResponse;
import com.dipdv.modules.report.dto.PaymentMethodSummary;
import com.dipdv.modules.report.dto.SalesSummaryResponse;
import com.dipdv.modules.report.dto.TopProductResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final EntityManager entityManager;

    /**
     * Total de vendas, número de pedidos e ticket médio por período.
     * Filtra apenas pedidos CLOSED com pelo menos 1 Payment PAID.
     */
    public SalesSummaryResponse getSalesSummary(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to) {

        Object[] result = (Object[]) entityManager.createNativeQuery("""
                SELECT
                    COUNT(DISTINCT o.id)                    AS order_count,
                    COALESCE(SUM(p.amount), 0)              AS total_revenue,
                    COALESCE(AVG(o.total), 0)               AS avg_ticket
                FROM orders o
                JOIN payments p ON p.order_id = o.id
                                AND p.status = 'PAID'
                                AND p.tenant_id = :tenantId
                WHERE o.tenant_id = :tenantId
                  AND o.status    = 'CLOSED'
                  AND o.closed_at BETWEEN :from AND :to
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        return new SalesSummaryResponse(
                ((Number) result[0]).longValue(),
                ((Number) result[1]).doubleValue(),
                ((Number) result[2]).doubleValue()
        );
    }

    /**
     * Top N produtos mais vendidos por quantidade no período.
     */
    @SuppressWarnings("unchecked")
    public List<TopProductResponse> getTopProducts(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to, int limit) {

        return entityManager.createNativeQuery("""
                SELECT
                    oi.product_id,
                    oi.product_name,
                    SUM(oi.quantity)        AS total_qty,
                    SUM(oi.total_price)     AS total_revenue
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.tenant_id  = :tenantId
                  AND o.status     = 'CLOSED'
                  AND o.closed_at  BETWEEN :from AND :to
                GROUP BY oi.product_id, oi.product_name
                ORDER BY total_qty DESC
                LIMIT :limit
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("limit", limit)
                .getResultList()
                .stream()
                .map(row -> {
                    Object[] r = (Object[]) row;
                    return new TopProductResponse(
                            UUID.fromString(r[0].toString()),
                            r[1].toString(),
                            ((Number) r[2]).longValue(),
                            ((Number) r[3]).doubleValue()
                    );
                })
                .toList();
    }

    /**
     * Faturamento total por forma de pagamento no período.
     */
    @SuppressWarnings("unchecked")
    public List<PaymentMethodSummary> getRevenueByPaymentMethod(
            UUID tenantId, OffsetDateTime from, OffsetDateTime to) {

        return entityManager.createNativeQuery("""
                SELECT
                    p.method,
                    COUNT(p.id)         AS transaction_count,
                    SUM(p.amount)       AS total_amount
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE p.tenant_id  = :tenantId
                  AND p.status     = 'PAID'
                  AND o.closed_at  BETWEEN :from AND :to
                GROUP BY p.method
                ORDER BY total_amount DESC
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()
                .stream()
                .map(row -> {
                    Object[] r = (Object[]) row;
                    return new PaymentMethodSummary(
                            r[0].toString(),
                            ((Number) r[1]).longValue(),
                            ((Number) r[2]).doubleValue()
                    );
                })
                .toList();
    }

    /**
     * Relatório de fechamento de um turno de caixa específico.
     */
    @SuppressWarnings("unchecked")
    public CashRegisterReportResponse getCashRegisterReport(
            UUID tenantId, UUID cashRegisterId) {

        Object[] cr = (Object[]) entityManager.createNativeQuery("""
                SELECT cr.opening_balance, cr.closing_balance,
                       cr.physical_balance, cr.difference,
                       cr.total_cash, cr.total_pix,
                       cr.opened_at, cr.closed_at,
                       u.name AS operator_name
                FROM cash_registers cr
                JOIN users u ON u.id = cr.opened_by
                WHERE cr.id = :id AND cr.tenant_id = :tenantId
                """)
                .setParameter("id", cashRegisterId)
                .setParameter("tenantId", tenantId)
                .getSingleResult();

        List<Object[]> movements = entityManager.createNativeQuery("""
                SELECT cm.type, cm.amount, cm.description, cm.created_at
                FROM cash_movements cm
                WHERE cm.cash_register_id = :id
                ORDER BY cm.created_at ASC
                """)
                .setParameter("id", cashRegisterId)
                .getResultList();

        return CashRegisterReportResponse.from(cr, movements);
    }
}
