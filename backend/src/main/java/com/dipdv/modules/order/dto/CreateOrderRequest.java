package com.dipdv.modules.order.dto;

import java.util.UUID;

public record CreateOrderRequest(
        UUID cashRegisterId   // opcional — nullable no MVP
) {}
