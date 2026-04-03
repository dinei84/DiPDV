package com.dipdv.modules.catalog.dto.modifier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ModifierGroupRequest(
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
    String name,

    @Min(value = 0, message = "Mínimo de seleção não pode ser negativo")
    Integer minSelect,

    @Min(value = 1, message = "Máximo de seleção deve ser pelo menos 1")
    Integer maxSelect,

    Boolean active,

    @Valid
    List<ModifierOptionRequest> options
) {
    public ModifierGroupRequest {
        if (minSelect == null) minSelect = 0;
        if (active == null) active = true;
    }
}