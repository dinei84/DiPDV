package com.dipdv.modules.catalog.controller;

import com.dipdv.modules.catalog.dto.modifier.ModifierGroupRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierGroupResponse;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionResponse;
import com.dipdv.modules.catalog.service.ModifierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Modificadores", description = "Grupos e opções de personalização de produtos")
public class ModifierController {

    private final ModifierService modifierService;

    // ── Modifier Groups ──────────────────────────────────────────────────────

    @GetMapping("/modifier-groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Listar grupos de modificadores")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<Page<ModifierGroupResponse>> listGroups(Pageable pageable) {
        return ResponseEntity.ok(modifierService.listGroups(pageable));
    }

    @GetMapping("/modifier-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Buscar grupo de modificadores por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo não encontrado")
    })
    public ResponseEntity<ModifierGroupResponse> getGroupById(@PathVariable UUID id) {
        return ResponseEntity.ok(modifierService.getGroupById(id));
    }

    @PostMapping("/modifier-groups")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar grupo de modificadores")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Grupo criado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "409", description = "Conflito (nome duplicado)")
    })
    public ResponseEntity<ModifierGroupResponse> createGroup(@RequestBody @Valid ModifierGroupRequest request) {
        ModifierGroupResponse response = modifierService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/modifier-groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar grupo de modificadores")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grupo atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito (nome duplicado)")
    })
    public ResponseEntity<ModifierGroupResponse> updateGroup(
            @PathVariable UUID id,
            @RequestBody @Valid ModifierGroupRequest request) {
        return ResponseEntity.ok(modifierService.updateGroup(id, request));
    }

    @DeleteMapping("/modifier-groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Inativar grupo de modificadores")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Grupo inativado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito (vinculado a produtos ativos)")
    })
    public ResponseEntity<Void> deactivateGroup(@PathVariable UUID id) {
        modifierService.deactivateGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ── Modifier Options ─────────────────────────────────────────────────────

    @PostMapping("/modifier-groups/{id}/options")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adicionar opção ao grupo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Opção adicionada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo não encontrado")
    })
    public ResponseEntity<ModifierOptionResponse> addOption(
            @PathVariable UUID id,
            @RequestBody @Valid ModifierOptionRequest request) {
        ModifierOptionResponse response = modifierService.addOption(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/modifier-groups/{groupId}/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar opção do grupo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opção atualizada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo ou Opção não encontrado")
    })
    public ResponseEntity<ModifierOptionResponse> updateOption(
            @PathVariable UUID groupId,
            @PathVariable UUID optionId,
            @RequestBody @Valid ModifierOptionRequest request) {
        return ResponseEntity.ok(modifierService.updateOption(groupId, optionId, request));
    }

    @DeleteMapping("/modifier-groups/{groupId}/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remover opção do grupo")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Opção removida"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Grupo ou Opção não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito (violação de minSelect)")
    })
    public ResponseEntity<Void> removeOption(
            @PathVariable UUID groupId,
            @PathVariable UUID optionId) {
        modifierService.removeOption(groupId, optionId);
        return ResponseEntity.noContent().build();
    }

    // ── Vínculos Produto ↔ Grupo ─────────────────────────────────────────────

    @GetMapping("/products/{productId}/modifiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Listar modificadores do produto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado")
    })
    public ResponseEntity<List<ModifierGroupResponse>> listProductModifiers(@PathVariable UUID productId) {
        return ResponseEntity.ok(modifierService.listProductModifiers(productId));
    }

    @PostMapping("/products/{productId}/modifiers/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vincular grupo a produto")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vínculo criado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Produto ou Grupo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito (vínculo já existe)")
    })
    public ResponseEntity<Void> linkGroupToProduct(
            @PathVariable UUID productId,
            @PathVariable UUID groupId,
            @RequestParam(required = false) Integer position) {
        modifierService.linkGroupToProduct(productId, groupId, position);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/products/{productId}/modifiers/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desvincular grupo do produto")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vínculo removido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado"),
            @ApiResponse(responseCode = "404", description = "Produto, Grupo ou Vínculo não encontrado")
    })
    public ResponseEntity<Void> unlinkGroupFromProduct(
            @PathVariable UUID productId,
            @PathVariable UUID groupId) {
        modifierService.unlinkGroupFromProduct(productId, groupId);
        return ResponseEntity.noContent().build();
    }
}