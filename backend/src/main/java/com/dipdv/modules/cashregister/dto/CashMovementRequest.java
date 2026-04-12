package com.dipdv.modules.cashregister.dto;

import com.dipdv.modules.cashregister.entity.enums.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashMovementRequest(
        @NotNull(message = "Tipo é obrigatório")
        CashMovementType type,

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo é R$ 0,01")
        BigDecimal amount,

        @NotBlank(message = "Descrição é obrigatória")
        String description
) {}
