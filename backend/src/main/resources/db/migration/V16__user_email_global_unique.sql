-- ============================================================================
-- V16 — User email global unique
-- Changes the user email uniqueness constraint from (tenant_id, email)
-- to global (email) for active users, allowing login with email only.
-- ============================================================================

DO $$
DECLARE
    duplicate_count INTEGER;
BEGIN
    -- Detectar colisões cross-tenant entre usuários ativos antes de migrar
    SELECT COUNT(*) INTO duplicate_count
    FROM (
        SELECT email FROM users WHERE active = true GROUP BY email HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION 'V16 abortada: % emails duplicados entre tenants ativos. Investigar antes de prosseguir.', duplicate_count;
    END IF;
END $$;

-- Drop do índice antigo da V15
DROP INDEX IF EXISTS uq_users_active_email;

-- Recriação como global único para usuários ativos
CREATE UNIQUE INDEX idx_users_email_active ON users (email) WHERE active = true;

COMMENT ON INDEX idx_users_email_active IS 'Garante que um email ativo seja único em toda a plataforma, permitindo login desambiguado.';

-- Atualização da policy de RLS para permitir busca global de email (necessário para login sem tenantId)
-- Mantemos o Kill Switch do SUPER_ADMIN (da V9) e adicionamos a nova flag de bypass para login.
DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR current_setting('app.bypass_rls_for_login', true) = 'true'
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );





