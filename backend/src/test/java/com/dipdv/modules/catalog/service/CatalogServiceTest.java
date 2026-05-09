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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CatalogService catalogService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
    }

    // ============================================
    // CENÁRIOS DE CATEGORIA
    // ============================================

    @Test
    void createCategory_whenValidRequest_shouldReturnResponse() {
        // Arrange
        CategoryRequest request = new CategoryRequest("Lanches", "hamburger");

        when(categoryRepository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "Lanches"))
                .thenReturn(false);

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        when(productRepository.countByTenantIdAndCategoryIdAndDeletedAtIsNull(any(UUID.class), any(UUID.class)))
                .thenReturn(0L);

        // Act
        CategoryResponse response = catalogService.createCategory(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("Lanches", response.name());
        assertEquals("hamburger", response.icon());
        assertFalse(response.isDefault());
        assertNull(response.deletedAt());

        verify(categoryRepository).existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "Lanches");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_whenNameAlreadyExists_shouldThrowConflict() {
        // Arrange
        CategoryRequest request = new CategoryRequest("Lanches", "hamburger");

        when(categoryRepository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "Lanches"))
                .thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.createCategory(request));

        assertEquals("Já existe uma categoria com este nome", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void getCategoryById_whenNotFound_shouldThrowNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.getCategoryById(id));

        assertEquals("Categoria não encontrada", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // ============================================
    // CENÁRIOS DE PRODUTO
    // ============================================

    @Test
    void createProduct_whenValidRequest_shouldReturnResponse() {
        // Arrange
        ProductRequest request = new ProductRequest(null, "X-Burguer", "Delicioso",
                new BigDecimal("25.00"));

        when(productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "X-Burguer"))
                .thenReturn(false);

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // Act
        ProductResponse response = catalogService.createProduct(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("X-Burguer", response.name());
        assertEquals(new BigDecimal("25.00"), response.price());
        assertNull(response.deletedAt());

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_whenNameAlreadyExists_shouldThrowConflict() {
        // Arrange
        ProductRequest request = new ProductRequest(null, "X-Burguer", "Delicioso",
                new BigDecimal("25.00"));

        when(productRepository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "X-Burguer"))
                .thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.createProduct(request));

        assertEquals("Já existe um produto com este nome", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_whenExists_shouldSetDeletedAt() {
        // Arrange
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .tenantId(TENANT_ID)
                .name("Produto Teste")
                .build();

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, TENANT_ID))
                .thenReturn(Optional.of(product));

        // Act
        catalogService.deleteProduct(productId);

        // Assert
        assertNotNull(product.getDeletedAt());
        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_whenNotFound_shouldThrowNotFound() {
        // Arrange
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, TENANT_ID))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.deleteProduct(productId));

        assertEquals("Produto não encontrado", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());

        verify(productRepository, never()).save(any());
    }

    @Test
    void getLowStockProducts_shouldReturnOnlyLowStockItems() {
        // Arrange
        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .name("Estoque Baixo 1")
                .stockQuantity(2)
                .stockMinLevel(5)
                .build();

        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .name("Estoque Baixo 2")
                .stockQuantity(0)
                .stockMinLevel(2)
                .build();

        when(productRepository.findLowStockProducts(TENANT_ID))
                .thenReturn(List.of(product1, product2));

        // Act
        List<ProductResponse> responses = catalogService.getLowStockProducts();

        // Assert
        assertEquals(2, responses.size());
        assertEquals("Estoque Baixo 1", responses.get(0).name());
        assertEquals("Estoque Baixo 2", responses.get(1).name());

        verify(productRepository).findLowStockProducts(TENANT_ID);
    }

    @Test
    void deleteCategory_whenIsDefault_shouldThrowBadRequest() {
        // Arrange
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .tenantId(TENANT_ID)
                .name("Diversos")
                .isDefault(true)
                .build();

        when(categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(categoryId, TENANT_ID))
                .thenReturn(Optional.of(category));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.deleteCategory(categoryId));

        assertEquals("Categoria padrão não pode ser excluída", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        verify(productRepository, never()).countByTenantIdAndCategoryId(any(), any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategory_whenHasLinkedProducts_shouldThrowBadRequest() {
        // Arrange
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .tenantId(TENANT_ID)
                .name("Lanches")
                .isDefault(false)
                .build();

        when(categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(categoryId, TENANT_ID))
                .thenReturn(Optional.of(category));

        when(productRepository.countByTenantIdAndCategoryId(TENANT_ID, categoryId))
                .thenReturn(2L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> catalogService.deleteCategory(categoryId));

        assertEquals("Categoria não pode ser excluída pois possui produtos vinculados", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        verify(categoryRepository, never()).save(any());
    }
}
