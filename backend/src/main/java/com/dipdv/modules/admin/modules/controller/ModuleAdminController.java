package com.dipdv.modules.admin.modules.controller;

import com.dipdv.shared.module.entity.Module;
import com.dipdv.shared.module.repository.ModuleRepository;
import com.dipdv.shared.module.service.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/modules")
@RequiredArgsConstructor
@Tag(name = "Admin — Módulos", description = "Gestão de módulos liberados por tenant (SUPER_ADMIN)")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModuleAdminController {

    private final ModuleService moduleService;
    private final ModuleRepository moduleRepository;

    @GetMapping("/catalog")
    @Operation(summary = "Lista todos os módulos do catálogo global")
    public ResponseEntity<List<Module>> getCatalog() {
        return ResponseEntity.ok(moduleRepository.findAll());
    }

    @GetMapping("/tenants/{tenantId}")
    @Operation(summary = "Lista módulos habilitados para um tenant específico")
    public ResponseEntity<List<String>> getTenantModules(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(moduleService.listEnabledModules(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/enable")
    @Operation(summary = "Habilita um módulo para o tenant")
    public ResponseEntity<Void> enableModule(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        
        String moduleCode = body.get("code");
        UUID actorUserId = UUID.fromString(auth.getName());
        
        moduleService.enableModule(tenantId, moduleCode, actorUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tenants/{tenantId}/disable")
    @Operation(summary = "Desabilita um módulo para o tenant")
    public ResponseEntity<Void> disableModule(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        
        String moduleCode = body.get("code");
        UUID actorUserId = UUID.fromString(auth.getName());
        
        try {
            moduleService.disableModule(tenantId, moduleCode, actorUserId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
