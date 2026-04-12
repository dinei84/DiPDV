package com.dipdv.modules.payment.dto;

import com.dipdv.modules.payment.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterPaymentRequest(
        @NotNull(message = "orderId é obrigatório")
        UUID orderId,

        @NotNull(message = "Método de pagamento é obrigatório")
        PaymentMethod method,

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo é R$ 0,01")
        BigDecimal amount,

        @NotBlank(message = "idempotencyKey é obrigatória")
        @Size(max = 64, message = "idempotencyKey deve ter no máximo 64 caracteres")
        String idempotencyKey
) {}
