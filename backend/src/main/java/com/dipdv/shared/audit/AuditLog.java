package com.dipdv.shared.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;                       // null se ação do sistema

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 60)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "ip_address", columnDefinition = "inet", insertable = false, updatable = false)
    private String ipAddress;

    @Column(name = "is_admin_action", nullable = false)
    @Builder.Default
    private boolean isAdminAction = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
