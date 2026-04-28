package com.dipdv.modules.admin.controller;

import com.dipdv.modules.admin.dto.TenantRequest;
import com.dipdv.modules.admin.dto.TenantResponse;
import com.dipdv.modules.admin.service.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin — Tenants", description = "CRUD de tenants para o painel SUPER_ADMIN")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    @GetMapping
    @Operation(summary = "Lista todos os tenants")
    public ResponseEntity<List<TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantAdminService.listTenants());
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Busca tenant por id")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenantAdminService.getTenant(tenantId));
    }

    @PostMapping
    @Operation(summary = "Cria um novo tenant")
    public ResponseEntity<TenantResponse> createTenant(
            @RequestBody TenantRequest request,
            Authentication authentication) {
        UUID actorUserId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantAdminService.createTenant(request, actorUserId));
    }

    @PutMapping("/{tenantId}")
    @Operation(summary = "Atualiza nome e status do tenant")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID tenantId,
            @RequestBody TenantRequest request) {
        return ResponseEntity.ok(tenantAdminService.updateTenant(tenantId, request));
    }
}
