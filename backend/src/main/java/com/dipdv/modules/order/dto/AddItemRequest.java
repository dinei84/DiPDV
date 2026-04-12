package com.dipdv.modules.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record AddItemRequest(
        @NotNull(message = "productId é obrigatório")
        UUID productId,

        @Min(value = 1, message = "Quantidade mínima é 1")
        Integer quantity,

        @Valid
        List<ModifierSelectionRequest> modifiers
) {
    public AddItemRequest {
        if (quantity == null) quantity = 1;
        if (modifiers == null) modifiers = new ArrayList<>();
    }

    public record ModifierSelectionRequest(
            @NotNull(message = "modifierOptionId é obrigatório")
            UUID modifierOptionId,

            @Min(value = 1, message = "Quantidade mínima é 1")
            Integer quantity
    ) {
        public ModifierSelectionRequest {
            if (quantity == null) quantity = 1;
        }
    }
}
