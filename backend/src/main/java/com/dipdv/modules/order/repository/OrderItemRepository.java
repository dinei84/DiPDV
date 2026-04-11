package com.dipdv.modules.order.repository;

import com.dipdv.modules.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            SELECT oi FROM OrderItem oi
            LEFT JOIN FETCH oi.modifiers m
            WHERE oi.orderId = :orderId
            ORDER BY oi.createdAt ASC
            """)
    List<OrderItem> findByOrderIdWithModifiers(@Param("orderId") UUID orderId);

    long countByOrderId(UUID orderId);

    Optional<OrderItem> findByIdAndOrderId(UUID id, UUID orderId);
}
