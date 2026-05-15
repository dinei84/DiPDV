package com.dipdv.modules.order.controller;

import com.dipdv.modules.order.dto.*;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import com.dipdv.modules.order.service.OrderService;
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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Gerenciamento de pedidos no PDV")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Criar pedido")
    public OrderResponse createOrder(@RequestBody(required = false) CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Buscar pedido completo com itens")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Listar pedidos (paginado, filtro por status)")
    public Page<OrderSummaryResponse> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.listOrders(status, pageable);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Adicionar item ao pedido")
    public OrderResponse addItem(
            @PathVariable UUID id,
            @RequestBody @Valid AddItemRequest request) {
        return orderService.addItem(id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Remover item do pedido")
    public OrderResponse removeItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId) {
        return orderService.removeItem(id, itemId);
    }

    @PatchMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Editar quantidade de item no pedido")
    public OrderResponse updateItemQuantity(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateItemQuantityRequest request) {
        return orderService.updateItemQuantity(id, itemId, request);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Cancelar pedido")
    public OrderResponse cancelOrder(
            @PathVariable UUID id,
            @RequestBody @Valid CancelOrderRequest request) {
        return orderService.cancelOrder(id, request.reason());
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Fechar pedido")
    public OrderResponse closeOrder(@PathVariable UUID id) {
        return orderService.closeOrder(id);
    }
}
