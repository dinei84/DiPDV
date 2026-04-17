package com.dipdv.modules.nfce.entity.enums;

public enum NfceStatus {
    PENDING,    // aguardando envio à SEFAZ
    ISSUED,     // emitida com sucesso
    REJECTED,   // rejeitada pela SEFAZ (erro nos dados)
    CANCELED    // cancelada após emissão
}
