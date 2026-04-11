package com.dipdv.modules.payment.dto;

import com.dipdv.modules.payment.entity.enums.PaymentMethod;
import com.dipdv.modules.payment.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal changeAmount,
        String gatewayRef,
        String idempotencyKey,
        String pixQrCode,          // mock QR Code PIX — null para CASH e em consultas
        OffsetDateTime createdAt
) {}
