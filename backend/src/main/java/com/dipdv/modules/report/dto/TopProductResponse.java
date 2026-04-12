package com.dipdv.modules.report.dto;

import java.util.UUID;

public record TopProductResponse(
        UUID productId,
        String productName,
        long totalQty,
        double totalRevenue
) {}
