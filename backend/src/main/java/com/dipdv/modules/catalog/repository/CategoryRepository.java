package com.dipdv.modules.catalog.repository;

import com.dipdv.modules.catalog.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Listar categorias não deletadas do tenant com paginação
    Page<Category> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    // Listar categorias incluindo deletadas
    Page<Category> findByTenantId(UUID tenantId, Pageable pageable);

    // Verificar duplicidade de nome no tenant (não deletadas)
    boolean existsByTenantIdAndNameAndDeletedAtIsNull(UUID tenantId, String name);

    // Verificar duplicidade de nome no tenant (inclusive deletadas)
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    // Buscar por id garantindo que pertence ao tenant (não deletada)
    Optional<Category> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    // Buscar por id (inclusive deletada)
    Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);
}
