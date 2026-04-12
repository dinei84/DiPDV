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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final NfceService nfceService;

    @Transactional
    public PaymentResponse registerPayment(RegisterPaymentRequest request) {
        UUID tenantId = TenantContext.getRequired();

        // 1. IDEMPOTÊNCIA
        Optional<Payment> existing = paymentRepository
                .findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Pagamento idempotente retornado: key={}", request.idempotencyKey());
            return toResponse(existing.get(), null);
        }

        // 2. VALIDAR CAIXA ABERTO
        CashRegister cashRegister = cashRegisterRepository
                .findByTenantIdAndStatus(tenantId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new BusinessException("Nenhum caixa aberto", HttpStatus.CONFLICT));

        // 3. VALIDAR ORDER
        Order order = orderRepository.findByIdAndTenantId(request.orderId(), tenantId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado", HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new BusinessException("Pedido não está fechado", HttpStatus.CONFLICT);
        }

        // 4. VALIDAR VALOR TOTAL
        BigDecimal somaPaga = paymentRepository.sumPaidAmountByOrderId(request.orderId());
        // PIX exige valor exato (não pode exceder o total restante)
        // CASH permite pagar a mais e receber troco
        if (request.method() != PaymentMethod.CASH) {
            if (somaPaga.add(request.amount()).compareTo(order.getTotal()) > 0) {
                throw new BusinessException("Valor excede o total do pedido", HttpStatus.CONFLICT);
            }
        } else {
            // CASH: só bloquear se o pedido já está completamente pago
            if (somaPaga.compareTo(order.getTotal()) >= 0) {
                throw new BusinessException("Pedido já está quitado", HttpStatus.CONFLICT);
            }
        }

        // 5. CALCULAR TROCO (apenas CASH)
        BigDecimal changeAmount = BigDecimal.ZERO;
        if (request.method() == PaymentMethod.CASH) {
            BigDecimal valorRestante = order.getTotal().subtract(somaPaga);
            BigDecimal troco = request.amount().subtract(valorRestante);
            changeAmount = troco.compareTo(BigDecimal.ZERO) > 0 ? troco : BigDecimal.ZERO;
        }

        // 6. CRIAR PAYMENT com status PENDING
        Payment payment = Payment.builder()
                .tenantId(tenantId)
                .orderId(request.orderId())
                .cashRegisterId(cashRegister.getId())
                .method(request.method())
                .status(PaymentStatus.PENDING)
                .amount(request.amount())
                .changeAmount(changeAmount)
                .idempotencyKey(request.idempotencyKey())
                .build();

        // 7. SIMULAR GATEWAY (mock)
        String pixQrCode = null;
        if (request.method() == PaymentMethod.CASH) {
            payment.setStatus(PaymentStatus.PAID);
        } else if (request.method() == PaymentMethod.PIX) {
            payment.setGatewayRef("PIX-MOCK-" + UUID.randomUUID());
            pixQrCode = "data:image/png;base64,MOCK_QR_CODE_" + UUID.randomUUID().toString().replace("-", "");
            payment.setStatus(PaymentStatus.PAID);
        }

        payment = paymentRepository.save(payment);

        // 8. ATUALIZAR TOTALIZADORES DO CAIXA
        if (payment.getStatus() == PaymentStatus.PAID) {
            if (request.method() == PaymentMethod.CASH) {
                cashRegister.setTotalCash(cashRegister.getTotalCash().add(payment.getAmount()));
            } else if (request.method() == PaymentMethod.PIX) {
                cashRegister.setTotalPix(cashRegister.getTotalPix().add(payment.getAmount()));
            }
            cashRegisterRepository.save(cashRegister);
        }

        // 9. VERIFICAR SE PEDIDO ESTÁ QUITADO
        BigDecimal novaSomaPaga = somaPaga.add(payment.getAmount());
        boolean pedidoQuitado = novaSomaPaga.compareTo(order.getTotal()) >= 0;

        if (pedidoQuitado) {
            orderService.deductStockForOrder(request.orderId());
        }

        // 10. EMITIR NFC-e (automático após pagamento total)
        if (pedidoQuitado) {
            try {
                final UUID paymentId = payment.getId();
                nfceService.emit(request.orderId(), paymentId);
            } catch (Exception e) {
                log.error("Falha ao emitir NFC-e para orderId={}: {}", request.orderId(), e.getMessage());
                // NÃO reverter pagamento — venda confirmada
            }
        }

        log.info("Pagamento registrado: {} method={} status={} orderId={}",
                payment.getId(), payment.getMethod(), payment.getStatus(), payment.getOrderId());

        return toResponse(payment, pixQrCode);
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId) {
        UUID tenantId = TenantContext.getRequired();

        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado", HttpStatus.NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException(
                    "Pagamento já confirmado não pode ser cancelado. Use estorno (pós-MVP).",
                    HttpStatus.CONFLICT);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                    "Apenas pagamentos PENDING podem ser cancelados",
                    HttpStatus.CONFLICT);
        }

        // Reverter totalizadores do caixa se necessário
        final PaymentMethod canceledMethod = payment.getMethod();
        final BigDecimal canceledAmount = payment.getAmount();
        cashRegisterRepository.findByIdAndTenantId(payment.getCashRegisterId(), tenantId)
                .ifPresent(register -> {
                    if (register.getStatus() == CashRegisterStatus.OPEN) {
                        if (canceledMethod == PaymentMethod.CASH) {
                            register.setTotalCash(register.getTotalCash().subtract(canceledAmount));
                        } else if (canceledMethod == PaymentMethod.PIX) {
                            register.setTotalPix(register.getTotalPix().subtract(canceledAmount));
                        }
                        cashRegisterRepository.save(register);
                    }
                });

        payment.setStatus(PaymentStatus.CANCELED);
        payment = paymentRepository.save(payment);

        log.info("Pagamento {} cancelado", paymentId);
        return toResponse(payment, null);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listOrderPayments(UUID orderId) {
        UUID tenantId = TenantContext.getRequired();
        return paymentRepository.findByOrderIdAndTenantId(orderId, tenantId).stream()
                .map(p -> toResponse(p, null))
                .toList();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private PaymentResponse toResponse(Payment payment, String pixQrCode) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getChangeAmount(),
                payment.getGatewayRef(),
                payment.getIdempotencyKey(),
                pixQrCode,
                payment.getCreatedAt()
        );
    }
}
