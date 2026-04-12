package com.dipdv.modules.inventory.repository;

import com.dipdv.modules.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
