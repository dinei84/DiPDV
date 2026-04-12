package com.dipdv.modules.catalog.service;

import com.dipdv.modules.catalog.dto.category.CategoryRequest;
import com.dipdv.modules.catalog.dto.category.CategoryResponse;
import com.dipdv.modules.catalog.dto.product.ProductRequest;
import com.dipdv.modules.catalog.dto.product.ProductResponse;
import com.dipdv.modules.catalog.entity.Category;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // ============================================
    // MÉTODOS DE CATEGORIA
    // ============================================

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategories(Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        return categoryRepository.findByTenantIdAndActiveTrueOrderByPositionAsc(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return toResponse(findCategoryByIdOrThrow(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        UUID tenantId = TenantContext.getRequired();

        // Validação: duplicidade de nome
        if (categoryRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.name())) {
            throw new BusinessException("Já existe uma categoria com este nome", HttpStatus.CONFLICT);
        }

        Category category = Category.builder()
                .tenantId(tenantId)
                .name(request.name())
                .position(request.position())
                .active(request.active())
                .build();

        category = categoryRepository.save(category);
        log.info("Categoria criada: {} pelo tenant: {}", category.getId(), tenantId);

        return toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategoryByIdOrThrow(id);

        if (!category.getName().equalsIgnoreCase(request.name()) &&
                categoryRepository.existsByTenantIdAndNameAndActiveTrue(category.getTenantId(), request.name())) {
            throw new BusinessException("Já existe uma categoria com este nome", HttpStatus.CONFLICT);
        }

        category.setName(request.name());
        category.setPosition(request.position());
        category.setActive(request.active());

        category = categoryRepository.save(category);
        log.info("Categoria atualizada: {} pelo tenant: {}", category.getId(), category.getTenantId());

        return toResponse(category);
    }

    @Transactional
    public void deactivateCategory(UUID id) {
        Category category = findCategoryByIdOrThrow(id);
        category.setActive(false);
        categoryRepository.save(category);
        log.info("Categoria inativada: {} pelo tenant: {}", id, category.getTenantId());
    }

    // ============================================
    // MÉTODOS DE PRODUTO
    // ============================================

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(UUID categoryId, Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();

        if (categoryId != null) {
            return productRepository.findByTenantIdAndCategoryIdAndActiveTrueAndDeletedAtIsNull(tenantId, categoryId, pageable)
                    .map(this::toResponse);
        }

        return productRepository.findByTenantIdAndActiveTrueAndDeletedAtIsNull(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        return toResponse(findProductByIdOrThrow(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        UUID tenantId = TenantContext.getRequired();

        if (productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.name())) {
            throw new BusinessException("Já existe um produto com este nome", HttpStatus.CONFLICT);
        }

        if (request.categoryId() != null) {
            findCategoryByIdOrThrow(request.categoryId()); // Garante que a categoria existe e pertence ao tenant
        }

        Product product = Product.builder()
                .tenantId(tenantId)
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .stockMinLevel(request.stockMinLevel())
                .active(request.active())
                .build();

        product = productRepository.save(product);
        log.info("Produto criado: {} pelo tenant: {}", product.getId(), tenantId);

        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProductByIdOrThrow(id);

        if (!product.getName().equalsIgnoreCase(request.name()) &&
                productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(product.getTenantId(), request.name())) {
            throw new BusinessException("Já existe um produto com este nome", HttpStatus.CONFLICT);
        }

        if (request.categoryId() != null && !request.categoryId().equals(product.getCategoryId())) {
            findCategoryByIdOrThrow(request.categoryId());
        }

        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setStockMinLevel(request.stockMinLevel());
        product.setActive(request.active());

        product = productRepository.save(product);
        log.info("Produto atualizado: {} pelo tenant: {}", product.getId(), product.getTenantId());

        return toResponse(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductByIdOrThrow(id);
        product.setDeletedAt(OffsetDateTime.now());
        product.setActive(false);
        // Não apagamos fisicamente. O Soft Delete entra em ação marcando deletedAt = now()
        productRepository.save(product);
        log.info("Produto deletado (soft delete): {} pelo tenant: {}", id, product.getTenantId());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        UUID tenantId = TenantContext.getRequired();
        return productRepository.findLowStockProducts(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ============================================
    // MÉTODOS PRIVADOS DE AUXÍLIO (Mappers / Fetch)
    // ============================================

    private Category findCategoryByIdOrThrow(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Categoria não encontrada", HttpStatus.NOT_FOUND));
    }

    private Product findProductByIdOrThrow(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getPosition(),
                category.getActive(),
                category.getCreatedAt()
        );
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStockMinLevel(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
