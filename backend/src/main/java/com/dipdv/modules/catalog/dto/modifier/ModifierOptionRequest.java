package com.dipdv.modules.catalog.dto.modifier;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModifierOptionRequest(
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
    String name,

    @DecimalMin(value = "0.00", message = "Acréscimo de preço não pode ser negativo")
    BigDecimal priceAddition,

    @Min(value = 1, message = "Quantidade máxima deve ser pelo menos 1")
    Integer maxQuantity,

    @Min(value = 0, message = "Posição não pode ser negativa")
    Integer position,

    Boolean active
) {
    public ModifierOptionRequest {
        if (priceAddition == null) priceAddition = BigDecimal.ZERO;
        if (maxQuantity == null) maxQuantity = 1;
        if (position == null) position = 0;
        if (active == null) active = true;
    }
}