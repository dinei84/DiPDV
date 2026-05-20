-- ============================================================================
-- V15 — Users active unique index
-- Allows reusing an email after user deactivation while blocking duplicates
-- among active users in the same tenant.
-- ============================================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_email_tenant;

CREATE UNIQUE INDEX uq_users_active_email
    ON users (tenant_id, email)
    WHERE active = true;

COMMENT ON INDEX uq_users_active_email IS 'Garante unicidade de email apenas para usuários ativos do mesmo tenant';
