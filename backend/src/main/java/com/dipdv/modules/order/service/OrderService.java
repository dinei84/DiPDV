package com.dipdv.modules.order.service;

import com.dipdv.modules.catalog.entity.ModifierGroup;
import com.dipdv.modules.catalog.entity.ModifierOption;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.repository.ModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.modules.inventory.entity.StockMovement;
import com.dipdv.modules.inventory.entity.enums.StockMovementType;
import com.dipdv.modules.inventory.repository.StockMovementRepository;
import com.dipdv.modules.order.dto.*;
import com.dipdv.modules.order.entity.Order;
import com.dipdv.modules.order.entity.OrderItem;
import com.dipdv.modules.order.entity.OrderItemModifier;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import com.dipdv.modules.order.repository.OrderItemRepository;
import com.dipdv.modules.order.repository.OrderRepository;
import com.dipdv.shared.audit.AuditAction;
import com.dipdv.shared.audit.Auditable;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import com.dipdv.shared.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TenantRepository tenantRepository;

    // ── Pedido ─────────────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        UUID tenantId = TenantContext.getRequired();
        UUID userId = extractUserId();

        Order order = Order.builder()
                .tenantId(tenantId)
                .userId(userId)
                .cashRegisterId(request != null ? request.cashRegisterId() : null)
                .status(OrderStatus.OPEN)
                .total(BigDecimal.ZERO)
                .build();

        order = orderRepository.save(order);
        log.info("Pedido criado: {} pelo tenant: {}", order.getId(), tenantId);

        return toOrderResponse(order, List.of());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        UUID tenantId = TenantContext.getRequired();
        Order order = findOrderOrThrow(orderId, tenantId);
        List<OrderItem> items = orderItemRepository.findByOrderIdWithModifiers(orderId);
        return toOrderResponse(order, items);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOrders(OrderStatus status, Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        } else {
            orders = orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return orders.map(o -> new OrderSummaryResponse(
                o.getId(),
                o.getStatus(),
                o.getTotal(),
                orderItemRepository.countByOrderId(o.getId()),
                o.getCreatedAt()
        ));
    }

    @Transactional
    public OrderResponse addItem(UUID orderId, AddItemRequest request) {
        UUID tenantId = TenantContext.getRequired();

        // 1. Buscar pedido
        Order order = findOrderOrThrow(orderId, tenantId);

        // 2. Validar status OPEN
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException(
                    "Pedido não está aberto para edição (status: " + order.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        // 3. Buscar produto no tenant
        Product product = productRepository
                .findByIdAndTenantIdAndDeletedAtIsNull(request.productId(), tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));

        // 4. Carregar grupos de modificadores do produto
        List<ModifierGroup> productGroups = modifierGroupRepository
                .findByProductIdWithOptions(product.getId(), tenantId);

        // 5. Validar seleções de modificadores
        validateModifierSelections(product, request.modifiers(), productGroups);

        // 6. Calcular totalPrice do item com modificadores
        BigDecimal modifierTotal = request.modifiers().stream()
                .map(sel -> {
                    ModifierOption option = findOptionInGroups(sel.modifierOptionId(), productGroups);
                    return option.getPriceAddition()
                            .multiply(BigDecimal.valueOf(sel.quantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.quantity()))
                .add(modifierTotal);

        // 7. Criar OrderItem com preços congelados
        OrderItem item = OrderItem.builder()
                .orderId(orderId)
                .productId(product.getId())
                .productName(product.getName())
                .quantity(request.quantity())
                .unitPrice(product.getPrice())
                .totalPrice(totalPrice)
                .build();

        // 8. Criar OrderItemModifiers com snapshots
        List<OrderItemModifier> modifiers = request.modifiers().stream()
                .map(sel -> {
                    ModifierOption option = findOptionInGroups(sel.modifierOptionId(), productGroups);
                    return OrderItemModifier.builder()
                            .modifierOptionId(option.getId())
                            .name(option.getName())
                            .priceAddition(option.getPriceAddition())
                            .quantity(sel.quantity())
                            .build();
                })
                .toList();

        item.getModifiers().addAll(modifiers);
        item = orderItemRepository.save(item);

        // 9. Atualizar orderItemId nos modificadores (após salvar o item)
        final UUID itemId = item.getId();
        item.getModifiers().forEach(m -> m.setOrderItemId(itemId));

        // 10. Recalcular Order.total
        List<OrderItem> allItems = orderItemRepository.findByOrderIdWithModifiers(orderId);
        BigDecimal newTotal = allItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotal(newTotal);
        orderRepository.save(order);

        log.info("Item adicionado ao pedido {}: produto {} qty {}", orderId, product.getId(), request.quantity());

        List<OrderItem> updatedItems = orderItemRepository.findByOrderIdWithModifiers(orderId);
        return toOrderResponse(order, updatedItems);
    }

    @Transactional
    public OrderResponse removeItem(UUID orderId, UUID itemId) {
        UUID tenantId = TenantContext.getRequired();
        Order order = findOrderOrThrow(orderId, tenantId);

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException(
                    "Pedido não está aberto para edição (status: " + order.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        OrderItem item = orderItemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new BusinessException("Item não encontrado no pedido", HttpStatus.NOT_FOUND));

        orderItemRepository.delete(item);

        // Recalcular total
        List<OrderItem> remaining = orderItemRepository.findByOrderIdWithModifiers(orderId);
        BigDecimal newTotal = remaining.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotal(newTotal);
        orderRepository.save(order);

        log.info("Item {} removido do pedido {}", itemId, orderId);

        return toOrderResponse(order, remaining);
    }

    @Transactional
    @Auditable(action = AuditAction.ORDER_CANCELED, entity = "orders")
    public OrderResponse cancelOrder(UUID orderId, String reason) {
        UUID tenantId = TenantContext.getRequired();
        Order order = findOrderOrThrow(orderId, tenantId);

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException(
                    "Pedido já está " + order.getStatus().name().toLowerCase() + " e não pode ser cancelado",
                    HttpStatus.CONFLICT);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Motivo do cancelamento é obrigatório", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.CANCELED);
        order.setCancelReason(reason);
        order.setClosedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Pedido {} cancelado. Motivo: {}", orderId, reason);

        List<OrderItem> items = orderItemRepository.findByOrderIdWithModifiers(orderId);
        return toOrderResponse(order, items);
    }

    @Transactional
    public OrderResponse closeOrder(UUID orderId) {
        UUID tenantId = TenantContext.getRequired();
        Order order = findOrderOrThrow(orderId, tenantId);

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessException(
                    "Pedido já está " + order.getStatus().name().toLowerCase() + " e não pode ser fechado",
                    HttpStatus.CONFLICT);
        }

        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(OffsetDateTime.now());
        orderRepository.save(order);

        // Atualizar last_activity_at do tenant
        tenantRepository.updateLastActivity(
            tenantId,
            OffsetDateTime.now()
        );

        log.info("Pedido {} fechado", orderId);

        // NÃO abater estoque aqui — ocorre no PaymentService ao confirmar PAID
        List<OrderItem> items = orderItemRepository.findByOrderIdWithModifiers(orderId);
        return toOrderResponse(order, items);
    }

    // ── Estoque (contrato para PaymentService) ──────────────────────────────────

    /**
     * Chamado pelo PaymentService quando Payment.status = PAID (Sprint 2).
     * Abate estoque e registra StockMovement por item.
     * NÃO lança exceção se estoque for insuficiente — apenas alerta.
     * Venda já foi confirmada e paga — não pode ser revertida por falta de estoque.
     */
    @Transactional
    public void deductStockForOrder(UUID orderId) {
        UUID tenantId = TenantContext.getRequired();
        Order order = findOrderOrThrow(orderId, tenantId);
        List<OrderItem> items = orderItemRepository.findByOrderIdWithModifiers(orderId);

        for (OrderItem item : items) {
            Product product = productRepository
                    .findByIdAndTenantIdAndDeletedAtIsNull(item.getProductId(), tenantId)
                    .orElse(null);

            if (product == null) {
                log.warn("Produto {} não encontrado ao abater estoque do pedido {}", item.getProductId(), orderId);
                continue;
            }

            int newQty = product.getStockQuantity() - item.getQuantity();
            product.setStockQuantity(Math.max(0, newQty)); // não vai negativo no campo
            productRepository.save(product);

            StockMovement movement = StockMovement.builder()
                    .tenantId(tenantId)
                    .productId(product.getId())
                    .userId(order.getUserId())
                    .type(StockMovementType.SALE)
                    .quantity(-item.getQuantity())
                    .reason("Venda confirmada - Pedido " + orderId)
                    .orderId(orderId)
                    .build();

            stockMovementRepository.save(movement);

            if (product.getStockQuantity() <= product.getStockMinLevel()) {
                log.warn("ALERTA ESTOQUE BAIXO: produto '{}' ({}) - estoque: {} / mínimo: {}",
                        product.getName(), product.getId(),
                        product.getStockQuantity(), product.getStockMinLevel());
            }
        }

        log.info("Estoque abatido para pedido {}: {} itens processados", orderId, items.size());
    }

    // ── Validação de modificadores ──────────────────────────────────────────────

    private void validateModifierSelections(
            Product product,
            List<AddItemRequest.ModifierSelectionRequest> selections,
            List<ModifierGroup> productGroups) {

        if (productGroups.isEmpty() && !selections.isEmpty()) {
            throw new BusinessException("Produto não aceita modificadores", HttpStatus.BAD_REQUEST);
        }

        for (ModifierGroup group : productGroups) {
            List<AddItemRequest.ModifierSelectionRequest> groupSelections = selections.stream()
                    .filter(s -> group.getOptions().stream()
                            .anyMatch(o -> o.getId().equals(s.modifierOptionId())))
                    .toList();

            int totalQty = groupSelections.stream()
                    .mapToInt(AddItemRequest.ModifierSelectionRequest::quantity)
                    .sum();

            if (totalQty < group.getMinSelect()) {
                throw new BusinessException(
                        "Grupo '" + group.getName() + "' exige ao menos " +
                                group.getMinSelect() + " seleção(ões)",
                        HttpStatus.BAD_REQUEST);
            }
            if (totalQty > group.getMaxSelect()) {
                throw new BusinessException(
                        "Grupo '" + group.getName() + "' aceita no máximo " +
                                group.getMaxSelect() + " seleção(ões)",
                        HttpStatus.BAD_REQUEST);
            }

            for (AddItemRequest.ModifierSelectionRequest sel : groupSelections) {
                ModifierOption option = group.getOptions().stream()
                        .filter(o -> o.getId().equals(sel.modifierOptionId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Opção de modificador não encontrada", HttpStatus.NOT_FOUND));

                if (sel.quantity() > option.getMaxQuantity()) {
                    throw new BusinessException(
                            "Opção '" + option.getName() + "' aceita no máximo " +
                                    option.getMaxQuantity() + " unidade(s)",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    // ── Helpers privados ────────────────────────────────────────────────────────

    private Order findOrderOrThrow(UUID orderId, UUID tenantId) {
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado", HttpStatus.NOT_FOUND));
    }

    private ModifierOption findOptionInGroups(UUID optionId, List<ModifierGroup> groups) {
        return groups.stream()
                .flatMap(g -> g.getOptions().stream())
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Opção de modificador não encontrada", HttpStatus.NOT_FOUND));
    }

    private UUID extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof DiPdvAuthDetails details) {
                return details.userId();
            }
        } catch (Exception ignored) {}
        throw new BusinessException("Usuário não autenticado", HttpStatus.UNAUTHORIZED);
    }

    private OrderResponse toOrderResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                order.getCashRegisterId(),
                itemResponses,
                order.getVersion(),
                order.getCreatedAt(),
                order.getClosedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        List<OrderItemModifierResponse> modifierResponses = item.getModifiers().stream()
                .map(m -> new OrderItemModifierResponse(
                        m.getId(),
                        m.getModifierOptionId(),
                        m.getName(),
                        m.getPriceAddition(),
                        m.getQuantity()
                ))
                .toList();

        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                modifierResponses
        );
    }
}
