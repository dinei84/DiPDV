-- =============================================================================
-- DiPDV — V4__add_modifier_quantity_fields.sql
-- Adiciona suporte a quantidade por opção de modificador
-- max_quantity: teto definido pelo admin na opção
-- quantity: registrado no OrderItemModifier ao finalizar o pedido
-- =============================================================================

-- Quantidade máxima que pode ser selecionada de uma opção (ex: máx 3 bacons)
ALTER TABLE modifier_options
    ADD COLUMN max_quantity SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_max_quantity_positive CHECK (max_quantity >= 1);

COMMENT ON COLUMN modifier_options.max_quantity IS
    'Teto de seleção por opção. Backend valida: quantity <= max_quantity ao finalizar pedido.';

-- Quantidade efetivamente pedida — registrada no OrderItemModifier
ALTER TABLE order_item_modifiers
    ADD COLUMN quantity SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_oim_quantity_positive CHECK (quantity >= 1);

COMMENT ON COLUMN order_item_modifiers.quantity IS
    'Quantidade selecionada pelo cliente. Validado: quantity <= modifier_options.max_quantity.';

-- Índice para consultas de opções ativas por grupo (frequente no PDV)
CREATE INDEX idx_modifier_options_group_active
    ON modifier_options (modifier_group_id, active, position)
    WHERE active = TRUE;
