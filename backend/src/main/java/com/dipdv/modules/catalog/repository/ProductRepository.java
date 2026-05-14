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

    // Listar produtos não deletados com paginação
    Page<Product> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    // Listar produtos incluindo deletados
    Page<Product> findByTenantId(UUID tenantId, Pageable pageable);

    // Filtrar por categoria (não deletados)
    Page<Product> findByTenantIdAndCategoryIdAndDeletedAtIsNull(
            UUID tenantId, UUID categoryId, Pageable pageable);

    // Filtrar por categoria incluindo deletados
    Page<Product> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);

    // Buscar por id (não deletado)
    Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    // Buscar por id (inclusive deletado)
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    // Contar produtos vinculados a uma categoria (não deletados)
    long countByTenantIdAndCategoryIdAndDeletedAtIsNull(UUID tenantId, UUID categoryId);

    // Contar produtos vinculados a uma categoria (inclusive deletados)
    long countByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    // Verificar duplicidade de nome no tenant
    boolean existsByTenantIdAndNameAndDeletedAtIsNull(UUID tenantId, String name);

    // Buscar produtos com estoque abaixo do mínimo (para alertas)
    @Query("""
        SELECT p FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.deletedAt IS NULL
          AND p.stockQuantity <= p.stockMinLevel
    """)
    List<Product> findLowStockProducts(@Param("tenantId") UUID tenantId);
}
