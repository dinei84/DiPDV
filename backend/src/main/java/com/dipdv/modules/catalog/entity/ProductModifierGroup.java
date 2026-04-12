package com.dipdv.modules.catalog.entity;

import com.dipdv.modules.catalog.entity.embedded.ProductModifierGroupId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "product_modifier_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductModifierGroup {

    @EmbeddedId
    private ProductModifierGroupId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("modifierGroupId")
    @JoinColumn(name = "modifier_group_id")
    private ModifierGroup modifierGroup;

    @JdbcTypeCode(java.sql.Types.SMALLINT)
    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;
}