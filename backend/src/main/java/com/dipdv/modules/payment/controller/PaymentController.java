package com.dipdv.modules.payment.controller;

import com.dipdv.modules.payment.dto.PaymentResponse;
import com.dipdv.modules.payment.dto.RegisterPaymentRequest;
import com.dipdv.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Registro e consulta de pagamentos")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Registrar pagamento")
    public PaymentResponse registerPayment(@RequestBody @Valid RegisterPaymentRequest request) {
        return paymentService.registerPayment(request);
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Listar pagamentos de um pedido")
    public List<PaymentResponse> listOrderPayments(@PathVariable UUID orderId) {
        return paymentService.listOrderPayments(orderId);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Cancelar pagamento PENDING")
    public PaymentResponse cancelPayment(@PathVariable UUID id) {
        return paymentService.cancelPayment(id);
    }
}
