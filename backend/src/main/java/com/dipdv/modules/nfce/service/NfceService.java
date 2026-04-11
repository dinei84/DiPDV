package com.dipdv.modules.nfce.service;

import com.dipdv.modules.nfce.entity.NfceDocument;

import java.util.UUID;

/**
 * Contrato para emissão de NFC-e.
 *
 * Implementação atual: MockNfceService (simula SEFAZ)
 * Implementação futura: NuvemFiscalNfceService
 *   Referência: https://dev.nuvemfiscal.com.br/docs/api/#tag/NFC-e
 *
 * Para trocar: criar NuvemFiscalNfceService implements NfceService,
 * anotar com @Primary e remover @Primary do MockNfceService.
 */
public interface NfceService {

    /**
     * Emite NFC-e para um pedido já pago.
     * @param orderId   ID do pedido
     * @param paymentId ID do pagamento que quitou o pedido
     * @return NfceDocument gerado
     */
    NfceDocument emit(UUID orderId, UUID paymentId);

    /**
     * Cancela uma NFC-e emitida.
     * Só é possível dentro da janela de cancelamento (30min no SEFAZ real).
     */
    NfceDocument cancel(UUID nfceDocumentId, String reason);
}
