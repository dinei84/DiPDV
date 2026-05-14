package com.dipdv.modules.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 80, message = "O nome não pode exceder 80 caracteres")
        String name,

        @Size(max = 50, message = "O ícone não pode exceder 50 caracteres")
        String icon,

        Integer position
) {
    public CategoryRequest {
        if (icon == null || icon.isBlank()) {
            icon = "package";
        }
        if (position == null) {
            position = 0;
        }
    }
}
