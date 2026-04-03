package com.dipdv.modules.catalog.repository;

import com.dipdv.modules.catalog.entity.ProductModifierGroup;
import com.dipdv.modules.catalog.entity.embedded.ProductModifierGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductModifierGroupRepository extends JpaRepository<ProductModifierGroup, ProductModifierGroupId> {

    boolean existsById(ProductModifierGroupId id);

    void deleteById(ProductModifierGroupId id);

    long countByIdProductId(UUID productId);
    
    long countByIdModifierGroupId(UUID modifierGroupId);
}