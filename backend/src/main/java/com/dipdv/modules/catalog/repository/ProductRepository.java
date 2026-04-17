package com.dipdv.modules.catalog.repository;

import com.dipdv.modules.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Listar produtos ativos e não deletados com paginação
    Page<Product> findByTenantIdAndActiveTrueAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    // Filtrar por categoria
    Page<Product> findByTenantIdAndCategoryIdAndActiveTrueAndDeletedAtIsNull(
            UUID tenantId, UUID categoryId, Pageable pageable);

    // Buscar por id (não deletado)
    Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    // Verificar duplicidade de nome no tenant
    boolean existsByTenantIdAndNameAndDeletedAtIsNull(UUID tenantId, String name);

    // Buscar produtos com estoque abaixo do mínimo (para alertas)
    @Query("""
        SELECT p FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.active = true
          AND p.deletedAt IS NULL
          AND p.stockQuantity <= p.stockMinLevel
    """)
    List<Product> findLowStockProducts(@Param("tenantId") UUID tenantId);
}
