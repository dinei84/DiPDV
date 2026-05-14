-- ============================================================================
-- V13 — Fix product unique constraint: absolute -> partial (active only)
-- This allows having multiple deleted products with the same name,
-- but only one active product per name per tenant.
-- Mirrors V12 change applied to categories.
-- ============================================================================

-- Remove old absolute constraint
ALTER TABLE products DROP CONSTRAINT IF EXISTS uq_product_name_tenant;

-- Create partial unique index for active products
CREATE UNIQUE INDEX uq_products_active_name ON products (tenant_id, name) WHERE (deleted_at IS NULL);

-- Add comment
COMMENT ON INDEX uq_products_active_name IS 'Garante unicidade de nome apenas para produtos ativos do mesmo tenant';
