package com.dipdv.modules.catalog.dto.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 80, message = "O nome não pode exceder 80 caracteres")
        String name,

        @Min(value = 0, message = "A posição não pode ser negativa")
        Integer position,

        Boolean active
) {
    public CategoryRequest {
        if (position == null) position = 0;
        if (active == null) active = true;
    }
}
