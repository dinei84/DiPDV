package com.dipdv.modules.payment.repository;

import com.dipdv.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Idempotência — chave única por tenant
    Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    // Soma dos valores PAID de um pedido (pagamento misto)
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.orderId = :orderId AND p.status = 'PAID'
            """)
    BigDecimal sumPaidAmountByOrderId(@Param("orderId") UUID orderId);

    List<Payment> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);
}
