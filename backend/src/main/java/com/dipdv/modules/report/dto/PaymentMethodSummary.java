package com.dipdv.modules.report.dto;

public record PaymentMethodSummary(
        String method,
        long transactionCount,
        double totalAmount
) {}
