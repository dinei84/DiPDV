package com.dipdv.shared.tenant.repository;

import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.enums.TenantPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Tenant> findByActiveTrue();

    List<Tenant> findByPlanType(TenantPlan planType);

    // Atualiza last_activity_at — chamado pelo OrderService
    @Modifying
    @Query("UPDATE Tenant t SET t.lastActivityAt = :now WHERE t.id = :tenantId")
    void updateLastActivity(@Param("tenantId") UUID tenantId,
                            @Param("now") OffsetDateTime now);
}
