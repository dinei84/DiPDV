-- ============================================================================
-- V12 — Fix category unique constraint: absolute -> partial (active only)
-- This allows having multiple deleted categories with the same name,
-- but only one active category per name per tenant.
-- ============================================================================

-- Remove old absolute constraints
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_tenant_id_name_unique;
ALTER TABLE categories DROP CONSTRAINT IF EXISTS uq_category_name_tenant;

-- Create partial unique index for active categories
CREATE UNIQUE INDEX uq_categories_active_name ON categories (tenant_id, name) WHERE (deleted_at IS NULL);

-- Add comment
COMMENT ON INDEX uq_categories_active_name IS 'Garante unicidade de nome apenas para categorias ativas do mesmo tenant';
