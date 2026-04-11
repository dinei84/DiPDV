package com.dipdv.modules.cashregister.repository;

import com.dipdv.modules.cashregister.entity.CashMovement;
import com.dipdv.modules.cashregister.entity.enums.CashMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findByCashRegisterIdOrderByCreatedAtAsc(UUID cashRegisterId);

    // Soma de movimentações por tipo — usado no cálculo de closingBalance
    @Query("""
            SELECT SUM(cm.amount) FROM CashMovement cm
            WHERE cm.cashRegisterId = :registerId AND cm.type = :type
            """)
    Optional<BigDecimal> sumAmountByRegisterIdAndType(
            @Param("registerId") UUID registerId,
            @Param("type") CashMovementType type
    );
}
