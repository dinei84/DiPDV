package com.dipdv.modules.nfce.controller;

import com.dipdv.modules.nfce.dto.NfceResponse;
import com.dipdv.modules.nfce.entity.NfceDocument;
import com.dipdv.modules.nfce.repository.NfceDocumentRepository;
import com.dipdv.modules.nfce.service.NfceService;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nfce")
@RequiredArgsConstructor
@Tag(name = "NFC-e", description = "Nota Fiscal do Consumidor Eletrônica")
public class NfceController {

    private final NfceService nfceService;
    private final NfceDocumentRepository nfceDocumentRepository;

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Buscar NFC-e de um pedido")
    public NfceResponse getByOrder(@PathVariable UUID orderId) {
        UUID tenantId = TenantContext.getRequired();
        NfceDocument doc = nfceDocumentRepository.findByOrderIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("NFC-e não encontrada para este pedido", HttpStatus.NOT_FOUND));
        return toResponse(doc);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Cancelar NFC-e emitida")
    public NfceResponse cancel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Cancelamento solicitado pelo operador") String reason) {
        NfceDocument doc = nfceService.cancel(id, reason);
        return toResponse(doc);
    }

    private NfceResponse toResponse(NfceDocument doc) {
        return new NfceResponse(
                doc.getId(),
                doc.getOrderId(),
                doc.getPaymentId(),
                doc.getStatus(),
                doc.getAccessKey(),
                doc.getProtocolNumber(),
                doc.getIssuedAt(),
                doc.getXmlContent(),
                doc.getRejectReason(),
                doc.getCanceledAt(),
                doc.getCreatedAt()
        );
    }
}
