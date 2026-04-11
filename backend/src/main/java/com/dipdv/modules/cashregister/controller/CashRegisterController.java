package com.dipdv.modules.cashregister.controller;

import com.dipdv.modules.cashregister.dto.*;
import com.dipdv.modules.cashregister.service.CashRegisterService;
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
@RequestMapping("/api/v1/cash-registers")
@RequiredArgsConstructor
@Tag(name = "Caixa", description = "Controle de turno e movimentações")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Abrir caixa")
    public CashRegisterResponse openCashRegister(
            @RequestBody(required = false) @Valid OpenCashRegisterRequest request) {
        if (request == null) request = new OpenCashRegisterRequest(null);
        return cashRegisterService.openCashRegister(request);
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Buscar caixa aberto atual")
    public CashRegisterResponse getOpenRegister() {
        return cashRegisterService.getOpenRegister();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Listar histórico de caixas")
    public Page<CashRegisterResponse> listRegisters(
            @PageableDefault(size = 20) Pageable pageable) {
        return cashRegisterService.listRegisters(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Buscar caixa por ID")
    public CashRegisterResponse getById(@PathVariable UUID id) {
        return cashRegisterService.getById(id);
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Registrar sangria ou suprimento")
    public CashMovementResponse addMovement(
            @PathVariable UUID id,
            @RequestBody @Valid CashMovementRequest request) {
        return cashRegisterService.addMovement(id, request);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Fechar caixa")
    public CashRegisterResponse closeCashRegister(
            @PathVariable UUID id,
            @RequestBody @Valid CloseCashRegisterRequest request) {
        return cashRegisterService.closeCashRegister(id, request);
    }
}
