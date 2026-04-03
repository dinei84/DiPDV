package com.dipdv.modules.catalog.dto.modifier;

import java.util.List;
import java.util.UUID;

public record ModifierGroupResponse(
    UUID id,
    String name,
    Integer minSelect,
    Integer maxSelect,
    Boolean active,
    List<ModifierOptionResponse> options
) {
}