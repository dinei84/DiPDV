-- =============================================================================
-- DiPDV — V5__add_order_item_product_name.sql
-- Adiciona snapshot do nome do produto em order_items
-- Necessário para exibição histórica mesmo após renomeação do produto
-- =============================================================================

ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(120) NOT NULL DEFAULT 'Produto';

COMMENT ON COLUMN order_items.product_name IS
    'Snapshot do nome do produto no momento da venda. Nunca atualizar retroativamente.';
