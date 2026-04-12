package com.dipdv.shared.audit;

import java.lang.annotation.*;

/**
 * Marca métodos de Service para interceptação pelo AuditAspect.
 * O método anotado deve ter o ID da entidade como primeiro parâmetro UUID.
 *
 * Uso:
 *   @Auditable(action = AuditAction.ORDER_CANCELED, entity = "orders")
 *   public OrderResponse cancelOrder(UUID orderId, String reason) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    AuditAction action();
    String entity();
}
