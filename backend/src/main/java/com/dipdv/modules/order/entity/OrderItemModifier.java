package com.dipdv.modules.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_item_modifiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemModifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "modifier_option_id", nullable = false)
    private UUID modifierOptionId;

    @Column(nullable = false, length = 80)
    private String name;                       // snapshot do nome da opção

    @Column(name = "price_addition", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal priceAddition = BigDecimal.ZERO; // snapshot do acréscimo

    @JdbcTypeCode(java.sql.Types.SMALLINT)
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;
}
