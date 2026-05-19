package com.dipdv.modules.order.service;

import com.dipdv.modules.catalog.entity.ModifierGroup;
import com.dipdv.modules.catalog.entity.ModifierOption;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.repository.ModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.modules.cashregister.entity.CashRegister;
import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import com.dipdv.modules.cashregister.repository.CashRegisterRepository;
import com.dipdv.modules.inventory.repository.StockMovementRepository;
import com.dipdv.modules.order.dto.AddItemRequest;
import com.dipdv.modules.order.dto.CreateOrderRequest;
import com.dipdv.modules.order.dto.OrderResponse;
import com.dipdv.modules.order.entity.Order;
import com.dipdv.modules.order.entity.OrderItem;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import com.dipdv.modules.order.repository.OrderItemRepository;
import com.dipdv.modules.order.repository.OrderRepository;
import com.dipdv.modules.payment.entity.enums.PaymentStatus;
import com.dipdv.modules.payment.repository.PaymentRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import com.dipdv.shared.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ModifierGroupRepository modifierGroupRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CashRegisterRepository cashRegisterRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderService orderService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);

        // Mock security context com usuário autenticado
        DiPdvAuthDetails authDetails = new DiPdvAuthDetails(USER_ID, TENANT_ID, "CASHIER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of());
        ((UsernamePasswordAuthenticationToken) auth).setDetails(authDetails);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        SecurityContextHolder.clearContext();
    }

    // ── Pedido ──────────────────────────────────────────────────────────────────

    @Test
    void createOrder_whenValid_shouldReturnOpenOrder() {
        UUID registerId = UUID.randomUUID();
        CashRegister cashRegister = CashRegister.builder().id(registerId).status(CashRegisterStatus.OPEN).build();
        
        Order saved = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .cashRegisterId(registerId)
                .status(OrderStatus.OPEN)
                .total(BigDecimal.ZERO)
                .version(0)
                .createdAt(OffsetDateTime.now())
                .build();

        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(cashRegister));
        when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(saved);

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(null));

        assertNotNull(response);
        assertEquals(OrderStatus.OPEN, response.status());
        assertEquals(BigDecimal.ZERO, response.total());
        assertEquals(registerId, response.cashRegisterId());
        assertEquals(0, response.version());
    }

    @Test
    void getOrder_whenNotFound_shouldThrowNotFound() {
        when(orderRepository.findByIdAndTenantId(any(), eq(TENANT_ID)))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrder(ORDER_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ── Itens ───────────────────────────────────────────────────────────────────

    @Test
    void addItem_whenOrderClosed_shouldThrowConflict() {
        Order closed = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.CLOSED)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(closed));

        AddItemRequest request = new AddItemRequest(PRODUCT_ID, 1, List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.addItem(ORDER_ID, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void addItem_whenProductSimple_shouldCreateItemWithoutModifiers() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).userId(USER_ID)
                .status(OrderStatus.OPEN).total(BigDecimal.ZERO).version(0)
                .build();

        Product product = Product.builder()
                .id(PRODUCT_ID).tenantId(TENANT_ID)
                .name("X-Burger").price(new BigDecimal("15.00"))
                .stockQuantity(10).stockMinLevel(2).active(true)
                .build();

        OrderItem savedItem = OrderItem.builder()
                .id(UUID.randomUUID()).orderId(ORDER_ID)
                .productId(PRODUCT_ID).productName("X-Burger")
                .quantity(1).unitPrice(new BigDecimal("15.00"))
                .totalPrice(new BigDecimal("15.00"))
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(modifierGroupRepository.findByProductIdWithOptions(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(savedItem);
        when(orderItemRepository.findByOrderIdWithModifiers(ORDER_ID)).thenReturn(List.of(savedItem));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.addItem(ORDER_ID, new AddItemRequest(PRODUCT_ID, 1, List.of()));

        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("X-Burger", response.items().get(0).productName());
        assertEquals(new BigDecimal("15.00"), response.items().get(0).unitPrice());
    }

    @Test
    void addItem_whenModifierQuantityExceedsMax_shouldThrowBadRequest() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).status(OrderStatus.OPEN).build();

        UUID optionId = UUID.randomUUID();
        ModifierOption option = ModifierOption.builder()
                .id(optionId).name("Bacon Extra")
                .priceAddition(new BigDecimal("2.00"))
                .maxQuantity(2).active(true).position(0)
                .build();

        ModifierGroup group = ModifierGroup.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID)
                .name("Adicionais").minSelect(0).maxSelect(5).active(true)
                .options(new ArrayList<>(List.of(option)))
                .build();

        Product product = Product.builder()
                .id(PRODUCT_ID).tenantId(TENANT_ID).name("X-Burger")
                .price(new BigDecimal("15.00")).stockQuantity(10).stockMinLevel(0)
                .active(true).build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(modifierGroupRepository.findByProductIdWithOptions(PRODUCT_ID, TENANT_ID)).thenReturn(List.of(group));

        // quantity=3 > maxQuantity=2
        AddItemRequest request = new AddItemRequest(PRODUCT_ID, 1,
                List.of(new AddItemRequest.ModifierSelectionRequest(optionId, 3)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.addItem(ORDER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("máximo"));
    }

    @Test
    void addItem_whenRequiredGroupNotSelected_shouldThrowBadRequest() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).status(OrderStatus.OPEN).build();

        ModifierOption option = ModifierOption.builder()
                .id(UUID.randomUUID()).name("Ao ponto")
                .priceAddition(BigDecimal.ZERO).maxQuantity(1).active(true).position(0)
                .build();

        // minSelect=1 exige pelo menos 1 seleção
        ModifierGroup group = ModifierGroup.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID)
                .name("Ponto da carne").minSelect(1).maxSelect(1).active(true)
                .options(new ArrayList<>(List.of(option)))
                .build();

        Product product = Product.builder()
                .id(PRODUCT_ID).tenantId(TENANT_ID).name("X-Burger")
                .price(new BigDecimal("15.00")).stockQuantity(10).stockMinLevel(0)
                .active(true).build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(modifierGroupRepository.findByProductIdWithOptions(PRODUCT_ID, TENANT_ID)).thenReturn(List.of(group));

        // Nenhuma seleção para grupo obrigatório
        AddItemRequest request = new AddItemRequest(PRODUCT_ID, 1, List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.addItem(ORDER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("exige ao menos"));
    }

    @Test
    void addItem_whenValid_shouldFreezeProductPrice() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).userId(USER_ID)
                .status(OrderStatus.OPEN).total(BigDecimal.ZERO).version(0)
                .build();

        BigDecimal originalPrice = new BigDecimal("20.00");
        Product product = Product.builder()
                .id(PRODUCT_ID).tenantId(TENANT_ID).name("Combo")
                .price(originalPrice).stockQuantity(5).stockMinLevel(1).active(true)
                .build();

        OrderItem savedItem = OrderItem.builder()
                .id(UUID.randomUUID()).orderId(ORDER_ID)
                .productId(PRODUCT_ID).productName("Combo")
                .quantity(2).unitPrice(originalPrice)
                .totalPrice(new BigDecimal("40.00"))
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(modifierGroupRepository.findByProductIdWithOptions(PRODUCT_ID, TENANT_ID)).thenReturn(List.of());
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(savedItem);
        when(orderItemRepository.findByOrderIdWithModifiers(ORDER_ID)).thenReturn(List.of(savedItem));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.addItem(ORDER_ID, new AddItemRequest(PRODUCT_ID, 2, List.of()));

        // unit_price deve ser o preço congelado no momento da adição
        assertEquals(originalPrice, response.items().get(0).unitPrice());
    }

    @Test
    void removeItem_whenOrderOpen_shouldRecalculateTotal() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).userId(USER_ID)
                .status(OrderStatus.OPEN).total(new BigDecimal("30.00")).version(1)
                .build();

        UUID itemId = UUID.randomUUID();
        OrderItem item = OrderItem.builder()
                .id(itemId).orderId(ORDER_ID).productId(PRODUCT_ID)
                .productName("X-Burger").quantity(1)
                .unitPrice(new BigDecimal("30.00")).totalPrice(new BigDecimal("30.00"))
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdAndOrderId(itemId, ORDER_ID)).thenReturn(Optional.of(item));
        when(orderItemRepository.findByOrderIdWithModifiers(ORDER_ID)).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.removeItem(ORDER_ID, itemId);

        assertNotNull(response);
        assertEquals(0, response.items().size());
        verify(orderItemRepository).delete(item);
    }

    // ── Cancelamento ────────────────────────────────────────────────────────────

    @Test
    void cancelOrder_whenAlreadyCanceled_shouldThrowConflict() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID)
                .status(OrderStatus.CANCELED).build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(ORDER_ID, "Motivo qualquer"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void cancelOrder_whenReasonBlank_shouldThrowBadRequest() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID)
                .status(OrderStatus.OPEN).build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(ORDER_ID, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void cancelOrder_whenValid_shouldSetStatusAndReason() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .status(OrderStatus.OPEN)
                .total(BigDecimal.ZERO)
                .version(0)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PAID)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdWithModifiers(ORDER_ID)).thenReturn(List.of());

        OrderResponse response = orderService.cancelOrder(ORDER_ID, "Pedido cancelado pelo cliente");

        assertEquals(OrderStatus.CANCELED, response.status());
        verify(orderRepository).save(any(Order.class));
    }


    // ── Estoque ─────────────────────────────────────────────────────────────────

    @Test
    void deductStockForOrder_shouldCreateStockMovementPerItem() {
        Order order = Order.builder()
                .id(ORDER_ID).tenantId(TENANT_ID).userId(USER_ID)
                .status(OrderStatus.CLOSED).build();

        Product product = Product.builder()
                .id(PRODUCT_ID).tenantId(TENANT_ID).name("X-Burger")
                .price(new BigDecimal("15.00")).stockQuantity(10).stockMinLevel(2)
                .active(true).build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID()).orderId(ORDER_ID)
                .productId(PRODUCT_ID).productName("X-Burger")
                .quantity(2).unitPrice(new BigDecimal("15.00"))
                .totalPrice(new BigDecimal("30.00"))
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdWithModifiers(ORDER_ID)).thenReturn(List.of(item));
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        orderService.deductStockForOrder(ORDER_ID);

        verify(stockMovementRepository, times(1)).save(any());
        assertEquals(8, product.getStockQuantity()); // 10 - 2
    }
}
