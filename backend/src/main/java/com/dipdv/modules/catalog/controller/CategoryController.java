package com.dipdv.modules.catalog.controller;

import com.dipdv.modules.catalog.dto.category.CategoryRequest;
import com.dipdv.modules.catalog.dto.category.CategoryResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Gerenciamento de categorias do cardápio")
public class CategoryController {

    private final CatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'SUPER_ADMIN')")
    @Operation(summary = "Listar categorias (paginado). Use ?includeDeleted=true para incluir deletadas.")
    public Page<CategoryResponse> listCategories(
            @PageableDefault(size = 20, sort = {"position", "name"}) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return catalogService.listCategories(pageable, includeDeleted);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'SUPER_ADMIN')")
    @Operation(summary = "Buscar categoria por id")
    public CategoryResponse getCategoryById(@PathVariable UUID id) {
        return catalogService.getCategoryById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Criar categoria")
    public CategoryResponse createCategory(@RequestBody @Valid CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Atualizar categoria")
    public CategoryResponse updateCategory(@PathVariable UUID id, @RequestBody @Valid CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Deletar categoria (soft delete)")
    public void deleteCategory(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    @Operation(summary = "Reativar categoria deletada")
    public CategoryResponse reactivateCategory(@PathVariable UUID id) {
        return catalogService.reactivateCategory(id);
    }
}
