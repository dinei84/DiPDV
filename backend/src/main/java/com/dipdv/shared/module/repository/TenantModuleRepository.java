package com.dipdv.shared.module.repository;

import com.dipdv.shared.module.entity.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantModuleRepository extends JpaRepository<TenantModule, Object> {

    @Query("SELECT tm.id.moduleCode FROM TenantModule tm WHERE tm.id.tenantId = :tenantId AND tm.enabled = true")
    List<String> findEnabledModuleCodesByTenantId(UUID tenantId);

    @Query("SELECT COUNT(tm) > 0 FROM TenantModule tm WHERE tm.id.tenantId = :tenantId AND tm.id.moduleCode = :moduleCode AND tm.enabled = true")
    boolean isModuleEnabledForTenant(UUID tenantId, String moduleCode);
}
