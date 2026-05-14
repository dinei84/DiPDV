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
    public Page<CategoryResponse> listCategories(Pageable pageable, boolean includeDeleted) {
        UUID tenantId = TenantContext.getRequired();
        Page<Category> page = includeDeleted
                ? categoryRepository.findByTenantId(tenantId, pageable)
                : categoryRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
        return page.map(this::toCategoryResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return toCategoryResponse(findCategoryByIdOrThrow(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        UUID tenantId = TenantContext.getRequired();

        // Validação: duplicidade de nome (apenas entre categorias ativas)
        if (categoryRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.name())) {
            throw new BusinessException("Já existe uma categoria ativa com este nome", HttpStatus.CONFLICT);
        }

        Category category = Category.builder()
                .tenantId(tenantId)
                .name(request.name())
                .icon(request.icon())
                .isDefault(false)
                .position(request.position())
                .build();

        category = categoryRepository.save(category);
        log.info("Categoria criada: {} pelo tenant: {}", category.getId(), tenantId);

        return toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategoryByIdOrThrow(id);

        if (!category.getName().equalsIgnoreCase(request.name()) &&
                categoryRepository.existsByTenantIdAndNameAndDeletedAtIsNull(category.getTenantId(), request.name())) {
            throw new BusinessException("Já existe uma categoria ativa com este nome", HttpStatus.CONFLICT);
        }

        category.setName(request.name());
        category.setIcon(request.icon());
        category.setPosition(request.position());

        category = categoryRepository.save(category);
        log.info("Categoria atualizada: {} pelo tenant: {}", category.getId(), category.getTenantId());

        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findCategoryByIdOrThrow(id);

        // Validação: não permite deletar categoria padrão
        if (category.getIsDefault()) {
            throw new BusinessException("Categoria padrão não pode ser excluída", HttpStatus.BAD_REQUEST);
        }

        // Validação: não permite deletar categoria com produtos vinculados (mesmo soft-deleted)
        long productCount = productRepository.countByTenantIdAndCategoryId(category.getTenantId(), id);
        if (productCount > 0) {
            throw new BusinessException("Categoria não pode ser excluída pois possui produtos vinculados", HttpStatus.BAD_REQUEST);
        }

        category.setDeletedAt(OffsetDateTime.now());
        categoryRepository.save(category);
        log.info("Categoria deletada (soft delete): {} pelo tenant: {}", id, category.getTenantId());
    }

    @Transactional
    public CategoryResponse reactivateCategory(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        Category category = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Categoria não encontrada", HttpStatus.NOT_FOUND));

        if (category.getDeletedAt() == null) {
            return toCategoryResponse(category);
        }

        // Se o constraint fosse parcial (só ativos), checaríamos se já existe uma ativa com mesmo nome
        // Como o constraint é absoluto no DB atual, se chegamos aqui com uma deletada,
        // tecnicamente não deveria existir outra com mesmo nome.
        // Mas vamos seguir a spec de retornar 409 se houver conflito.
        if (categoryRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, category.getName())) {
            throw new BusinessException("Já existe uma categoria ativa com este nome. Renomeie a categoria existente antes de reativar.", HttpStatus.CONFLICT);
        }

        category.setDeletedAt(null);
        category = categoryRepository.save(category);
        log.info("Categoria reativada: {} pelo tenant: {}", id, tenantId);
        return toCategoryResponse(category);
    }

    // ============================================
    // MÉTODOS DE PRODUTO
    // ============================================

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(UUID categoryId, Pageable pageable, boolean includeDeleted) {
        UUID tenantId = TenantContext.getRequired();

        Page<Product> page;
        if (categoryId != null) {
            page = includeDeleted
                    ? productRepository.findByTenantIdAndCategoryId(tenantId, categoryId, pageable)
                    : productRepository.findByTenantIdAndCategoryIdAndDeletedAtIsNull(tenantId, categoryId, pageable);
        } else {
            page = includeDeleted
                    ? productRepository.findByTenantId(tenantId, pageable)
                    : productRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
        }

        return page.map(this::toProductResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        return toProductResponse(findProductByIdOrThrow(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        UUID tenantId = TenantContext.getRequired();

        if (productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.name())) {
            throw new BusinessException("Já existe um produto com este nome", HttpStatus.CONFLICT);
        }

        // Validação: categoria deve existir se informada
        if (request.categoryId() != null) {
            findCategoryByIdOrThrow(request.categoryId());
        }

        Product product = Product.builder()
                .tenantId(tenantId)
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0)
                .stockMinLevel(request.stockMinLevel() != null ? request.stockMinLevel() : 0)
                .build();

        product = productRepository.save(product);
        log.info("Produto criado: {} pelo tenant: {}", product.getId(), tenantId);

        return toProductResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProductByIdOrThrow(id);

        if (!product.getName().equalsIgnoreCase(request.name()) &&
                productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(product.getTenantId(), request.name())) {
            throw new BusinessException("Já existe um produto com este nome", HttpStatus.CONFLICT);
        }

        // Validação: categoria deve existir se informada
        if (request.categoryId() != null && !request.categoryId().equals(product.getCategoryId())) {
            findCategoryByIdOrThrow(request.categoryId());
        }

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategoryId(request.categoryId());
        if (request.stockQuantity() != null) {
            product.setStockQuantity(request.stockQuantity());
        }
        if (request.stockMinLevel() != null) {
            product.setStockMinLevel(request.stockMinLevel());
        }

        product = productRepository.save(product);
        log.info("Produto atualizado: {} pelo tenant: {}", product.getId(), product.getTenantId());

        return toProductResponse(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductByIdOrThrow(id);
        product.setDeletedAt(OffsetDateTime.now());
        productRepository.save(product);
        log.info("Produto deletado (soft delete): {} pelo tenant: {}", id, product.getTenantId());
    }

    @Transactional
    public ProductResponse reactivateProduct(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        Product product = productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));

        if (product.getDeletedAt() == null) {
            return toProductResponse(product);
        }

        if (productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, product.getName())) {
            throw new BusinessException("Já existe um produto ativo com este nome. Renomeie o produto existente antes de reativar.", HttpStatus.CONFLICT);
        }

        product.setDeletedAt(null);
        product = productRepository.save(product);
        log.info("Produto reativado: {} pelo tenant: {}", id, tenantId);
        return toProductResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        UUID tenantId = TenantContext.getRequired();
        return productRepository.findLowStockProducts(tenantId).stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());
    }

    // ============================================
    // MÉTODOS PRIVADOS DE AUXÍLIO
    // ============================================

    private Category findCategoryByIdOrThrow(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new BusinessException("Categoria não encontrada", HttpStatus.NOT_FOUND));
    }

    private Product findProductByIdOrThrow(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));
    }

    private CategoryResponse toCategoryResponse(Category category) {
        long productCount = productRepository.countByTenantIdAndCategoryIdAndDeletedAtIsNull(
                category.getTenantId(), category.getId());

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getIsDefault(),
                category.getPosition(),
                productCount,
                category.getDeletedAt(),
                category.getCreatedAt()
        );
    }

    private ProductResponse toProductResponse(Product product) {
        String categoryName = null;
        String categoryIcon = null;

        if (product.getCategoryId() != null) {
            Category category = categoryRepository.findByIdAndTenantId(product.getCategoryId(), product.getTenantId())
                    .orElse(null);
            if (category != null) {
                categoryName = category.getName();
                categoryIcon = category.getIcon();
            }
        }

        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                categoryName,
                categoryIcon,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStockMinLevel(),
                product.getDeletedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
