-- =============================================================================
-- DiPDV — V6__create_nfce_documents.sql
-- Tabela de documentos NFC-e vinculados a pedidos
-- =============================================================================

CREATE TYPE nfce_status AS ENUM (
    'PENDING',    -- aguardando envio à SEFAZ
    'ISSUED',     -- emitida com sucesso
    'REJECTED',   -- rejeitada pela SEFAZ (erro nos dados)
    'CANCELED'    -- cancelada após emissão
);

CREATE TABLE nfce_documents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    order_id        UUID        NOT NULL REFERENCES orders(id)  ON DELETE RESTRICT,
    payment_id      UUID        REFERENCES payments(id)         ON DELETE SET NULL,
    status          nfce_status NOT NULL DEFAULT 'PENDING',
    access_key      VARCHAR(44),           -- chave de acesso SEFAZ (44 dígitos)
    protocol_number VARCHAR(20),           -- número do protocolo de autorização
    issued_at       TIMESTAMPTZ,           -- data/hora de autorização pela SEFAZ
    xml_content     TEXT,                  -- XML da NFC-e (mock ou real)
    reject_reason   TEXT,                  -- motivo de rejeição se status = REJECTED
    canceled_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_nfce_order UNIQUE (order_id)  -- 1 NFC-e por pedido
);

-- RLS
ALTER TABLE nfce_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE nfce_documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON nfce_documents
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- Índices
CREATE INDEX idx_nfce_tenant_status ON nfce_documents (tenant_id, status);
CREATE INDEX idx_nfce_order_id      ON nfce_documents (order_id);

COMMENT ON TABLE  nfce_documents            IS 'Documentos NFC-e emitidos por pedido';
COMMENT ON COLUMN nfce_documents.access_key IS 'Chave de 44 dígitos gerada pela SEFAZ. Mock: dados fictícios no formato correto.';
