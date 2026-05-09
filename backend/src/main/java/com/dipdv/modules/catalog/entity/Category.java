package com.dipdv.modules.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String name;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String icon = "package";

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.SMALLINT)
    @Column(columnDefinition = "SMALLINT DEFAULT 0")
    private Integer position = 0;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Builder.Default
    @Column(nullable = false)
    @Deprecated(forRemoval = true, since = "soft delete via deletedAt")
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
