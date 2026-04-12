package com.dipdv.modules.payment.service;

import com.dipdv.modules.cashregister.entity.CashRegister;
import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import com.dipdv.modules.cashregister.repository.CashRegisterRepository;
import com.dipdv.modules.nfce.service.NfceService;
import com.dipdv.modules.order.entity.Order;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import com.dipdv.modules.order.repository.OrderRepository;
import com.dipdv.modules.order.service.OrderService;
import com.dipdv.modules.payment.dto.PaymentResponse;
import com.dipdv.modules.payment.dto.RegisterPaymentRequest;
import com.dipdv.modules.payment.entity.Payment;
import com.dipdv.modules.payment.entity.enums.PaymentMethod;
import com.dipdv.modules.payment.entity.enums.PaymentStatus;
import com.dipdv.modules.payment.repository.PaymentRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CashRegisterRepository cashRegisterRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private NfceService nfceService;

    @InjectMocks
    private PaymentService paymentService;

    private static final UUID TENANT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID    = UUID.randomUUID();
    private static final UUID REGISTER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID  = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
    }

    // ── Idempotência ────────────────────────────────────────────────────────────

    @Test
    void registerPayment_whenIdempotencyKeyExists_shouldReturnExistingPayment() {
        Payment existing = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("18.90"));
        when(paymentRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH, new BigDecimal("18.90"), IDEMPOTENCY_KEY));

        assertEquals(existing.getId(), response.id());
        verify(paymentRepository, never()).save(any());
    }

    // ── Validação de caixa ──────────────────────────────────────────────────────

    @Test
    void registerPayment_whenNoCashRegisterOpen_shouldThrowConflict() {
        when(paymentRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.registerPayment(
                        new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                                new BigDecimal("18.90"), IDEMPOTENCY_KEY)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Nenhum caixa aberto"));
    }

    // ── Validação de pedido ─────────────────────────────────────────────────────

    @Test
    void registerPayment_whenOrderNotClosed_shouldThrowConflict() {
        when(paymentRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(buildCashRegister()));

        Order openOrder = buildOrder(OrderStatus.OPEN, new BigDecimal("18.90"));
        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(openOrder));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.registerPayment(
                        new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                                new BigDecimal("18.90"), IDEMPOTENCY_KEY)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Pedido não está fechado"));
    }

    @Test
    void registerPayment_whenAmountExceedsTotal_shouldThrowConflict() {
        // PIX exige valor exato — exceder o total do pedido é inválido
        when(paymentRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(buildCashRegister()));

        Order closedOrder = buildOrder(OrderStatus.CLOSED, new BigDecimal("10.00"));
        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(closedOrder));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.registerPayment(
                        new RegisterPaymentRequest(ORDER_ID, PaymentMethod.PIX,
                                new BigDecimal("20.00"), IDEMPOTENCY_KEY)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Valor excede o total do pedido"));
    }

    // ── Fluxo CASH ──────────────────────────────────────────────────────────────

    @Test
    void registerPayment_whenCash_shouldCalculateChange() {
        setupValidOrderAndRegister(new BigDecimal("18.90"));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Payment saved = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("20.00"));
        saved.setChangeAmount(new BigDecimal("1.10"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(orderService).deductStockForOrder(any());
        when(nfceService.emit(any(), any())).thenReturn(null);

        PaymentResponse response = paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                        new BigDecimal("20.00"), IDEMPOTENCY_KEY));

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.status());
        assertEquals(new BigDecimal("1.10"), response.changeAmount());
    }

    // ── Fluxo PIX ───────────────────────────────────────────────────────────────

    @Test
    void registerPayment_whenPix_shouldGenerateMockGatewayRef() {
        setupValidOrderAndRegister(new BigDecimal("18.90"));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Payment saved = buildPayment(PaymentMethod.PIX, PaymentStatus.PAID, new BigDecimal("18.90"));
        saved.setGatewayRef("PIX-MOCK-" + UUID.randomUUID());
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(orderService).deductStockForOrder(any());
        when(nfceService.emit(any(), any())).thenReturn(null);

        PaymentResponse response = paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.PIX,
                        new BigDecimal("18.90"), IDEMPOTENCY_KEY));

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.status());
        assertTrue(response.gatewayRef().startsWith("PIX-MOCK-"));
    }

    // ── Integração estoque e NFC-e ──────────────────────────────────────────────

    @Test
    void registerPayment_whenOrderFullyPaid_shouldCallDeductStock() {
        setupValidOrderAndRegister(new BigDecimal("18.90"));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Payment saved = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("18.90"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nfceService.emit(any(), any())).thenReturn(null);

        paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                        new BigDecimal("18.90"), IDEMPOTENCY_KEY));

        verify(orderService).deductStockForOrder(ORDER_ID);
    }

    @Test
    void registerPayment_whenOrderFullyPaid_shouldCallNfceEmit() {
        setupValidOrderAndRegister(new BigDecimal("18.90"));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Payment saved = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("18.90"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(orderService).deductStockForOrder(any());
        when(nfceService.emit(any(), any())).thenReturn(null);

        paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                        new BigDecimal("18.90"), IDEMPOTENCY_KEY));

        verify(nfceService).emit(eq(ORDER_ID), any());
    }

    @Test
    void registerPayment_whenNfceEmitFails_shouldNotRevertPayment() {
        setupValidOrderAndRegister(new BigDecimal("18.90"));
        when(paymentRepository.sumPaidAmountByOrderId(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Payment saved = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("18.90"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(orderService).deductStockForOrder(any());
        when(nfceService.emit(any(), any())).thenThrow(new RuntimeException("SEFAZ indisponível"));

        // Não deve lançar exceção — NFC-e falha mas pagamento persiste
        assertDoesNotThrow(() -> paymentService.registerPayment(
                new RegisterPaymentRequest(ORDER_ID, PaymentMethod.CASH,
                        new BigDecimal("18.90"), IDEMPOTENCY_KEY)));

        // Payment ainda deve ter sido salvo
        verify(paymentRepository).save(any(Payment.class));
    }

    // ── cancelPayment ───────────────────────────────────────────────────────────

    @Test
    void cancelPayment_whenStatusPaid_shouldThrowConflict() {
        Payment paid = buildPayment(PaymentMethod.CASH, PaymentStatus.PAID, new BigDecimal("18.90"));
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID)).thenReturn(Optional.of(paid));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.cancelPayment(PAYMENT_ID));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void setupValidOrderAndRegister(BigDecimal orderTotal) {
        when(paymentRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cashRegisterRepository.findByTenantIdAndStatus(TENANT_ID, CashRegisterStatus.OPEN))
                .thenReturn(Optional.of(buildCashRegister()));
        Order closedOrder = buildOrder(OrderStatus.CLOSED, orderTotal);
        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(closedOrder));
    }

    private CashRegister buildCashRegister() {
        return CashRegister.builder()
                .id(REGISTER_ID)
                .tenantId(TENANT_ID)
                .openedBy(UUID.randomUUID())
                .status(CashRegisterStatus.OPEN)
                .openingBalance(new BigDecimal("100.00"))
                .totalCash(BigDecimal.ZERO)
                .totalCard(BigDecimal.ZERO)
                .totalPix(BigDecimal.ZERO)
                .openedAt(OffsetDateTime.now())
                .build();
    }

    private Order buildOrder(OrderStatus status, BigDecimal total) {
        return Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .userId(UUID.randomUUID())
                .status(status)
                .total(total)
                .version(0)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private Payment buildPayment(PaymentMethod method, PaymentStatus status, BigDecimal amount) {
        return Payment.builder()
                .id(PAYMENT_ID)
                .tenantId(TENANT_ID)
                .orderId(ORDER_ID)
                .cashRegisterId(REGISTER_ID)
                .method(method)
                .status(status)
                .amount(amount)
                .changeAmount(BigDecimal.ZERO)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
