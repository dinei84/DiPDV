package com.dipdv.modules.nfce.dto;

import com.dipdv.modules.nfce.entity.enums.NfceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NfceResponse(
        UUID id,
        UUID orderId,
        UUID paymentId,
        NfceStatus status,
        String accessKey,
        String protocolNumber,
        OffsetDateTime issuedAt,
        String xmlContent,
        String rejectReason,
        OffsetDateTime canceledAt,
        OffsetDateTime createdAt
) {}
