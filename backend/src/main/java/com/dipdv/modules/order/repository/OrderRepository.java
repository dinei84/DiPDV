package com.dipdv.modules.order.repository;

import com.dipdv.modules.order.entity.Order;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Order> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Order> findByTenantIdAndStatus(UUID tenantId, OrderStatus status);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.tenantId = :tenantId")
    Optional<Order> findByIdAndTenantIdWithLock(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );
}
