package com.dipdv.modules.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
        @NotBlank(message = "Motivo do cancelamento é obrigatório")
        String reason
) {}
