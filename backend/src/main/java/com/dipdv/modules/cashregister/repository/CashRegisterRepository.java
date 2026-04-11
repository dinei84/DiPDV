package com.dipdv.modules.cashregister.repository;

import com.dipdv.modules.cashregister.entity.CashRegister;
import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CashRegisterRepository extends JpaRepository<CashRegister, UUID> {

    // Buscar caixa por status — a exclusion constraint garante no máximo 1 OPEN por tenant
    Optional<CashRegister> findByTenantIdAndStatus(UUID tenantId, CashRegisterStatus status);

    // Histórico paginado — mais recente primeiro
    Page<CashRegister> findByTenantIdOrderByOpenedAtDesc(UUID tenantId, Pageable pageable);

    Optional<CashRegister> findByIdAndTenantId(UUID id, UUID tenantId);
}
