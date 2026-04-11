package com.dipdv.modules.nfce.entity;

import com.dipdv.modules.nfce.entity.enums.NfceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nfce_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NfceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "nfce_status")
    @Builder.Default
    private NfceStatus status = NfceStatus.PENDING;

    @Column(name = "access_key", length = 44)
    private String accessKey;

    @Column(name = "protocol_number", length = 20)
    private String protocolNumber;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
