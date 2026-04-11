package com.dipdv.modules.payment.entity.enums;

public enum PaymentStatus {
    PENDING,    // aguardando confirmação
    PAID,       // confirmado
    FAILED,     // erro no processamento
    CANCELED,   // cancelado pelo operador
    REFUNDED    // estornado (pós-MVP, modelado mas não implementado)
}
