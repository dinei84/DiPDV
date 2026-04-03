package com.dipdv.modules.catalog.entity.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductModifierGroupId implements Serializable {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "modifier_group_id")
    private UUID modifierGroupId;
}