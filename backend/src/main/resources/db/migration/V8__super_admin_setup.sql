-- =============================================================================
-- DiPDV — V8__super_admin_setup.sql
-- Infraestrutura SUPER_ADMIN: tenant master, novos ENUMs, RLS atualizado
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Novos ENUMs
-- -----------------------------------------------------------------------------
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'SUPER_ADMIN';

DO $$ BEGIN
    CREATE TYPE tenant_plan AS ENUM ('TRIAL', 'ACTIVE', 'SUSPENDED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- -----------------------------------------------------------------------------
-- 2. Atualizar tabela tenants
-- -----------------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS plan_type       tenant_plan  NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN IF NOT EXISTS trial_until     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS owner_email     VARCHAR(180),
    ADD COLUMN IF NOT EXISTS slug            VARCHAR(60);

COMMENT ON COLUMN tenants.owner_email      IS 'Email do primeiro usuário ADMIN criado no onboarding';
COMMENT ON COLUMN tenants.last_activity_at IS 'Atualizado pelo OrderService ao fechar pedido — indica tenant ativo';
COMMENT ON COLUMN tenants.slug             IS 'Identificador amigável para futuras URLs customizadas';

-- -----------------------------------------------------------------------------
-- 3. Adicionar is_admin_action ao audit_log
-- -----------------------------------------------------------------------------
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS is_admin_action BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN audit_log.is_admin_action IS
    'TRUE quando a ação foi executada pelo SUPER_ADMIN — facilita queries de compliance';

-- -----------------------------------------------------------------------------
-- 4. Criar tenant master
-- -----------------------------------------------------------------------------
INSERT INTO tenants (id, name, slug, active, plan_type)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'DiPDV Master',
    'master',
    TRUE,
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Policy de auto-update para tenants (last_activity_at)
-- Permite que o contexto de um tenant atualize apenas sua própria linha
-- -----------------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_self_update ON tenants;
CREATE POLICY tenant_self_update ON tenants
    FOR UPDATE
    USING (id = current_setting('app.current_tenant', true)::UUID);

-- -----------------------------------------------------------------------------
-- 6. Atualizar RLS em TODAS as tabelas — adicionar Kill Switch
--
-- Padrão do Kill Switch (dupla verificação):
--   OR (
--     current_setting('app.is_super_admin', true) = 'true'
--     AND current_setting('app.current_tenant', true) = 'ffffffff-...'
--   )
--
-- Procedimento: DROP policy existente + CREATE nova com Kill Switch
-- -----------------------------------------------------------------------------

-- users
DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (
            current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
        )
    );

-- categories
DROP POLICY IF EXISTS tenant_isolation ON categories;
CREATE POLICY tenant_isolation ON categories
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- products
DROP POLICY IF EXISTS tenant_isolation ON products;
CREATE POLICY tenant_isolation ON products
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- modifier_groups
DROP POLICY IF EXISTS tenant_isolation ON modifier_groups;
CREATE POLICY tenant_isolation ON modifier_groups
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- cash_registers
DROP POLICY IF EXISTS tenant_isolation ON cash_registers;
CREATE POLICY tenant_isolation ON cash_registers
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- orders
DROP POLICY IF EXISTS tenant_isolation ON orders;
CREATE POLICY tenant_isolation ON orders
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- payments
DROP POLICY IF EXISTS tenant_isolation ON payments;
CREATE POLICY tenant_isolation ON payments
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- stock_movements
DROP POLICY IF EXISTS tenant_isolation ON stock_movements;
CREATE POLICY tenant_isolation ON stock_movements
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- audit_log
DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- nfce_documents
DROP POLICY IF EXISTS tenant_isolation ON nfce_documents;
CREATE POLICY tenant_isolation ON nfce_documents
    USING (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    )
    WITH CHECK (
        tenant_id = current_setting('app.current_tenant', true)::UUID
        OR (current_setting('app.is_super_admin', true) = 'true'
            AND current_setting('app.current_tenant', true)
                = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
    );

-- -----------------------------------------------------------------------------
-- 7. Índices novos
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_tenants_plan_type      ON tenants (plan_type);
CREATE INDEX IF NOT EXISTS idx_tenants_last_activity  ON tenants (last_activity_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_audit_log_admin_action ON audit_log (is_admin_action, created_at DESC)
    WHERE is_admin_action = TRUE;
