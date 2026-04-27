package com.dipdv.shared.module.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantModule {

    @EmbeddedId
    private TenantModuleId id;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "enabled_at", updatable = false)
    private OffsetDateTime enabledAt;

    @Column(name = "enabled_by")
    private UUID enabledBy;
}
