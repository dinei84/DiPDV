package com.dipdv.modules.catalog.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        UUID categoryId,

        @NotBlank(message = "O nome do produto é obrigatório")
        @Size(max = 120, message = "O nome não pode exceder 120 caracteres")
        String name,

        @Size(max = 500, message = "A descrição não pode exceder 500 caracteres")
        String description,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        BigDecimal price,

        @Min(value = 0, message = "A quantidade de estoque não pode ser negativa")
        Integer stockQuantity,

        @Min(value = 0, message = "O nível mínimo de estoque não pode ser negativo")
        Integer stockMinLevel
) {
}
