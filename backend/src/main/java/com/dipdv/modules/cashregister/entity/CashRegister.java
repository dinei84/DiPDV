package com.dipdv.modules.cashregister.entity;

import com.dipdv.modules.cashregister.entity.enums.CashRegisterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_registers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(name = "closed_by")
    private UUID closedBy;                     // nullable — preenchido no fechamento

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "cash_register_status")
    @Builder.Default
    private CashRegisterStatus status = CashRegisterStatus.OPEN;

    @Column(name = "opening_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 10, scale = 2)
    private BigDecimal closingBalance;         // calculado no fechamento

    @Column(name = "physical_balance", precision = 10, scale = 2)
    private BigDecimal physicalBalance;        // informado pelo operador no fechamento

    @Column(precision = 10, scale = 2)
    private BigDecimal difference;             // physicalBalance - closingBalance

    @Column(name = "total_cash", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCash;              // acumulado de pagamentos CASH

    @Column(name = "total_card", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCard;              // acumulado de pagamentos CARD (pós-MVP)

    @Column(name = "total_pix", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPix;               // acumulado de pagamentos PIX

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
