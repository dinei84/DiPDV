-- ============================================================================
-- V11 — Add icon and is_default to categories, backfill "Diversos"
-- ============================================================================

-- Add new columns to categories
ALTER TABLE categories
ADD COLUMN icon VARCHAR(50) NOT NULL DEFAULT 'package',
ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;

-- Ensure UNIQUE (tenant_id, name) constraint exists
-- Check if constraint already exists to avoid duplicates
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.constraint_column_usage
    WHERE table_name = 'categories'
    AND constraint_name = 'categories_tenant_id_name_unique'
  ) THEN
    ALTER TABLE categories
    ADD CONSTRAINT categories_tenant_id_name_unique
    UNIQUE (tenant_id, name);
  END IF;
END $$;

-- Backfill "Diversos" category for all existing tenants (except those that already have it)
INSERT INTO categories (tenant_id, name, icon, is_default, position, active, created_at, updated_at)
SELECT
  t.id,
  'Diversos',
  'package',
  true,
  0,
  true,
  NOW(),
  NOW()
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1 FROM categories c
  WHERE c.tenant_id = t.id
  AND c.name = 'Diversos'
)
AND t.active = true
ON CONFLICT (tenant_id, name) DO NOTHING;

-- Add deletedAt column to categories (for consistency with products)
ALTER TABLE categories
ADD COLUMN deleted_at TIMESTAMPTZ NULL;

-- Comment for tracking
COMMENT ON COLUMN categories.icon IS 'Icon name for UI rendering (e.g., package, food, shop)';
COMMENT ON COLUMN categories.is_default IS 'Default category for uncategorized products; cannot be deleted';
COMMENT ON COLUMN categories.deleted_at IS 'Soft delete timestamp; NULL means active record';
