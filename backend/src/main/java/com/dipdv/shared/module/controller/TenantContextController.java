package com.dipdv.shared.module.controller;

import com.dipdv.shared.module.service.ModuleService;
import com.dipdv.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Contexto — Usuário", description = "Informações do usuário logado e seu estabelecimento")
public class TenantContextController {

    private final ModuleService moduleService;

    @GetMapping("/modules")
    @Operation(summary = "Lista módulos habilitados para o estabelecimento do usuário atual")
    public ResponseEntity<List<String>> getMyModules() {
        UUID tenantId = TenantContext.getRequired();
        return ResponseEntity.ok(moduleService.listEnabledModules(tenantId));
    }
}
