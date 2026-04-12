package com.dipdv.modules.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashRegisterRequest(
        @NotNull(message = "Saldo físico é obrigatório")
        @DecimalMin(value = "0.00", message = "Saldo físico não pode ser negativo")
        BigDecimal physicalBalance
) {}
