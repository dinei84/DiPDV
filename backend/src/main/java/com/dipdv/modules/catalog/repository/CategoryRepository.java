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

    // Listar categorias ativas do tenant com paginação
    Page<Category> findByTenantIdAndActiveTrueOrderByPositionAsc(UUID tenantId, Pageable pageable);

    // Verificar duplicidade de nome no tenant
    boolean existsByTenantIdAndNameAndActiveTrue(UUID tenantId, String name);

    // Buscar por id garantindo que pertence ao tenant (RLS já filtra, mas boa prática)
    Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);
}
