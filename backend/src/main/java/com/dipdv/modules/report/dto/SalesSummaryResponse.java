package com.dipdv.modules.report.dto;

public record SalesSummaryResponse(
        long orderCount,
        double totalRevenue,
        double avgTicket
) {}
