-- =============================================================================
-- DiPDV — V10__create_modules.sql
-- Sistema de Feature Gating: Catálogo de módulos e ativação por tenant
-- =============================================================================

-- 1. Catálogo global de módulos
CREATE TABLE modules (
    code        TEXT         PRIMARY KEY,
    name        TEXT         NOT NULL,
    description TEXT,
    tier        TEXT         NOT NULL CHECK (tier IN ('BASE', 'PAID')),
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

COMMENT ON TABLE modules IS 'Catálogo global de funcionalidades liberáveis do sistema';

-- 2. Ativação de módulos por tenant
CREATE TABLE tenant_modules (
    tenant_id   UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    module_code TEXT         NOT NULL REFERENCES modules(code) ON DELETE CASCADE,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled_at  TIMESTAMPTZ  DEFAULT NOW(),
    enabled_by  UUID,        -- ID do usuário SUPER_ADMIN que ativou (opcional)
    
    PRIMARY KEY (tenant_id, module_code)
);

COMMENT ON TABLE tenant_modules IS 'Relação de módulos habilitados para cada estabelecimento';

-- Habilitar RLS na tenant_modules
ALTER TABLE tenant_modules ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_modules FORCE ROW LEVEL SECURITY;

-- Política de isolamento com Kill Switch para SUPER_ADMIN
CREATE POLICY tenant_isolation ON tenant_modules
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    );

-- 3. Seed dos módulos no catálogo
INSERT INTO modules (code, name, description, tier) VALUES
    ('PDV_BASIC', 'PDV Básico', 'Operações fundamentais de venda e caixa', 'BASE'),
    ('CATALOG_MANAGEMENT', 'Gestão de Catálogo', 'Cadastro de produtos, categorias e modificadores', 'BASE'),
    ('PAYMENT_PIX', 'Pagamento via PIX', 'Integração para recebimento via PIX dinâmico', 'PAID'),
    ('PAYMENT_CARD', 'Pagamento via Cartão', 'Integração com máquinas de cartão/TEF', 'PAID'),
    ('REPORTS', 'Relatórios Avançados', 'Dashboard de vendas, produtos mais vendidos e BI', 'PAID'),
    ('INVENTORY', 'Gestão de Estoque', 'Controle de entradas, saídas e ficha técnica', 'PAID'),
    ('WHATSAPP_ORDERS', 'Pedidos via WhatsApp', 'Integração com catálogo online e WhatsApp', 'PAID'),
    ('IFOOD_INTEGRATION', 'Integração iFood', 'Gestão de pedidos do iFood dentro do PDV', 'PAID'),
    ('LOYALTY', 'Programa de Fidelidade', 'Gestão de pontos e cashback para clientes', 'PAID');

-- 4. Backfill: Ativar módulos BASE para todos os tenants existentes
-- Garante que nenhum tenant perca acesso às funções básicas após a migração
INSERT INTO tenant_modules (tenant_id, module_code)
SELECT t.id, m.code
FROM tenants t, modules m
WHERE m.tier = 'BASE'
ON CONFLICT DO NOTHING;
