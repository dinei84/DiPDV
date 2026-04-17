package com.dipdv.modules.catalog.controller;

import com.dipdv.modules.catalog.dto.product.ProductRequest;
import com.dipdv.modules.catalog.dto.product.ProductResponse;
import com.dipdv.modules.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Gerenciamento de produtos do cardápio")
public class ProductController {

    private final CatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Listar produtos (paginado)")
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return catalogService.listProducts(categoryId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Buscar produto por id")
    public ProductResponse getProductById(@PathVariable UUID id) {
        return catalogService.getProductById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar produto")
    public ProductResponse createProduct(@RequestBody @Valid ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar produto")
    public ProductResponse updateProduct(@PathVariable UUID id, @RequestBody @Valid ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete")
    public void deleteProduct(@PathVariable UUID id) {
        catalogService.deleteProduct(id);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Produtos com estoque baixo")
    public List<ProductResponse> getLowStockProducts() {
        return catalogService.getLowStockProducts();
    }
}
