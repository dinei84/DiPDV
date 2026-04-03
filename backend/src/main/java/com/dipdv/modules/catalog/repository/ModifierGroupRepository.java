package com.dipdv.modules.catalog.repository;

import com.dipdv.modules.catalog.entity.ModifierGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID> {

    Page<ModifierGroup> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId, Pageable pageable);

    Optional<ModifierGroup> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameAndActiveTrue(UUID tenantId, String name);

    @Query("""
        SELECT mg FROM ModifierGroup mg
        LEFT JOIN FETCH mg.options o
        WHERE mg.id IN (
            SELECT pmg.modifierGroup.id FROM ProductModifierGroup pmg
            WHERE pmg.product.id = :productId
              AND pmg.product.tenantId = :tenantId
        )
        AND mg.active = true
        ORDER BY mg.id
    """)
    List<ModifierGroup> findByProductIdWithOptions(
        @Param("productId") UUID productId,
        @Param("tenantId") UUID tenantId
    );
}