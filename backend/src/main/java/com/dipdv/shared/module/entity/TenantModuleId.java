package com.dipdv.shared.module.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class TenantModuleId implements Serializable {
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "module_code")
    private String moduleCode;
}
