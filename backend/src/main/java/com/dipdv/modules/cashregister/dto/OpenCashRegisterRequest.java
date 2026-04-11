package com.dipdv.modules.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record OpenCashRegisterRequest(
        @DecimalMin(value = "0.00", message = "Saldo inicial não pode ser negativo")
        BigDecimal openingBalance
) {
    public OpenCashRegisterRequest {
        if (openingBalance == null) openingBalance = BigDecimal.ZERO;
    }
}
