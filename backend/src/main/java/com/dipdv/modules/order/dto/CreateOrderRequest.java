package com.dipdv.modules.order.dto;

import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @Size(max = 100)
        String identifier
) {}
