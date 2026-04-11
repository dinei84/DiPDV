package com.dipdv.modules.nfce.service;

import com.dipdv.modules.nfce.entity.NfceDocument;
import com.dipdv.modules.nfce.entity.enums.NfceStatus;
import com.dipdv.modules.nfce.repository.NfceDocumentRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// TODO: substituir por NuvemFiscalNfceService quando tiver certificado digital
// Referência: https://dev.nuvemfiscal.com.br/docs/api/#tag/NFC-e
@Primary
@Service
@Slf4j
@RequiredArgsConstructor
public class MockNfceService implements NfceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final NfceDocumentRepository nfceDocumentRepository;

    @Override
    @Transactional
    public NfceDocument emit(UUID orderId, UUID paymentId) {
        UUID tenantId = TenantContext.getRequired();

        // Verificar se já existe NFC-e para este pedido (idempotência)
        return nfceDocumentRepository.findByOrderIdAndTenantId(orderId, tenantId)
                .orElseGet(() -> {
                    String mockAccessKey = generateMockAccessKey();
                    String mockProtocol  = "135" + System.currentTimeMillis();

                    NfceDocument doc = NfceDocument.builder()
                            .tenantId(tenantId)
                            .orderId(orderId)
                            .paymentId(paymentId)
                            .status(NfceStatus.ISSUED)
                            .accessKey(mockAccessKey)
                            .protocolNumber(mockProtocol)
                            .issuedAt(OffsetDateTime.now())
                            .xmlContent(generateMockXml(orderId, mockAccessKey))
                            .build();

                    log.info("[MOCK NFC-e] Emitida para orderId={} accessKey={}", orderId, mockAccessKey);

                    return nfceDocumentRepository.save(doc);
                });
    }

    @Override
    @Transactional
    public NfceDocument cancel(UUID nfceDocumentId, String reason) {
        UUID tenantId = TenantContext.getRequired();

        NfceDocument doc = nfceDocumentRepository
                .findByIdAndTenantId(nfceDocumentId, tenantId)
                .orElseThrow(() -> new BusinessException("NFC-e não encontrada", HttpStatus.NOT_FOUND));

        if (doc.getStatus() != NfceStatus.ISSUED) {
            throw new BusinessException(
                    "Apenas NFC-e emitidas podem ser canceladas", HttpStatus.CONFLICT);
        }

        doc.setStatus(NfceStatus.CANCELED);
        doc.setCanceledAt(OffsetDateTime.now());
        log.info("[MOCK NFC-e] Cancelada: id={} reason={}", nfceDocumentId, reason);
        return nfceDocumentRepository.save(doc);
    }

    private String generateMockAccessKey() {
        // 44 dígitos numéricos — formato SEFAZ simplificado para mock
        // Formato real: cUF(2) + AAMM(4) + CNPJ(14) + mod(2) + serie(3) + nNF(9) + tpEmis(1) + cNF(8) + cDV(1)
        String invoiceNumber = String.format("%09d", System.currentTimeMillis() % 1_000_000_000L);
        String randomCode = String.format("%08d", RANDOM.nextInt(100_000_000));
        String checkDigit = Integer.toString(RANDOM.nextInt(10));
        return "35"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"))
                + "00000000000000"
                + "65"
                + "001"
                + invoiceNumber
                + "1"
                + randomCode
                + checkDigit;
    }

    private String generateMockXml(UUID orderId, String accessKey) {
        return String.format(
                "<nfeProc versao=\"4.00\"><NFe><infNFe Id=\"NFe%s\">" +
                "<ide><cUF>35</cUF><mod>65</mod></ide>" +
                "<emit><CNPJ>00000000000000</CNPJ><xNome>DiPDV Mock</xNome></emit>" +
                "</infNFe></NFe></nfeProc>", accessKey);
    }
}
