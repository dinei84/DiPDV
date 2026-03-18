-- =============================================================================
-- DiPDV — V3__indexes.sql
-- Índices de performance e unicidade
-- Separado do schema base para facilitar ajuste sem re-executar DDL completo
-- =============================================================================

-- -----------------------------------------------------------------------------
-- USERS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_users_tenant_id       ON users (tenant_id);
CREATE INDEX idx_users_tenant_email    ON users (tenant_id, email)
    WHERE deleted_at IS NULL;  -- partial index: apenas usuários ativos

-- -----------------------------------------------------------------------------
-- CATEGORIES
-- -----------------------------------------------------------------------------
CREATE INDEX idx_categories_tenant_id  ON categories (tenant_id)
    WHERE active = TRUE;

-- -----------------------------------------------------------------------------
-- PRODUCTS
-- Índices mais importantes do sistema — consultados a cada abertura do PDV
-- -----------------------------------------------------------------------------
CREATE INDEX idx_products_tenant_id       ON products (tenant_id)
    WHERE active = TRUE AND deleted_at IS NULL;

CREATE INDEX idx_products_tenant_category ON products (tenant_id, category_id)
    WHERE active = TRUE AND deleted_at IS NULL;

-- Para alertas de estoque baixo
CREATE INDEX idx_products_low_stock ON products (tenant_id, stock_quantity, stock_min_level)
    WHERE active = TRUE AND deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- MODIFIER_GROUPS / OPTIONS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_modifier_groups_tenant  ON modifier_groups (tenant_id);
CREATE INDEX idx_modifier_options_group  ON modifier_options (modifier_group_id)
    WHERE active = TRUE;
CREATE INDEX idx_product_modifier_groups_product   ON product_modifier_groups (product_id);
CREATE INDEX idx_product_modifier_groups_group     ON product_modifier_groups (modifier_group_id);

-- -----------------------------------------------------------------------------
-- CASH_REGISTERS
-- Consulta frequente: caixa aberto do tenant atual
-- -----------------------------------------------------------------------------
CREATE INDEX idx_cash_registers_tenant_status ON cash_registers (tenant_id, status);
CREATE INDEX idx_cash_registers_opened_at     ON cash_registers (tenant_id, opened_at DESC);

-- -----------------------------------------------------------------------------
-- CASH_MOVEMENTS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_cash_movements_register ON cash_movements (cash_register_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- ORDERS
-- Consultas mais frequentes do sistema — cada item do PDV gera um lookup aqui
-- -----------------------------------------------------------------------------
CREATE INDEX idx_orders_tenant_status     ON orders (tenant_id, status);
CREATE INDEX idx_orders_tenant_created    ON orders (tenant_id, created_at DESC);
CREATE INDEX idx_orders_cash_register     ON orders (cash_register_id);
CREATE INDEX idx_orders_user              ON orders (tenant_id, user_id);

-- Para relatórios por período (RF04)
CREATE INDEX idx_orders_tenant_closed_at  ON orders (tenant_id, closed_at DESC)
    WHERE status = 'CLOSED';

-- -----------------------------------------------------------------------------
-- ORDER_ITEMS
-- Cobrem as policies RLS de ORDER_ITEMS e ORDER_ITEM_MODIFIERS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_order_items_order_id     ON order_items (order_id);
CREATE INDEX idx_order_items_product_id   ON order_items (product_id);  -- relatório top produtos

-- -----------------------------------------------------------------------------
-- ORDER_ITEM_MODIFIERS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_order_item_modifiers_item ON order_item_modifiers (order_item_id);

-- -----------------------------------------------------------------------------
-- PAYMENTS
-- idempotency_key já tem UNIQUE — o índice é criado automaticamente
-- Índice adicional para relatório de faturamento por forma de pagamento
-- -----------------------------------------------------------------------------
CREATE INDEX idx_payments_order_id         ON payments (order_id);
CREATE INDEX idx_payments_tenant_status    ON payments (tenant_id, status);
CREATE INDEX idx_payments_tenant_method    ON payments (tenant_id, method, created_at DESC)
    WHERE status = 'PAID';  -- partial: apenas pagamentos confirmados nos relatórios

-- -----------------------------------------------------------------------------
-- STOCK_MOVEMENTS
-- -----------------------------------------------------------------------------
CREATE INDEX idx_stock_movements_product   ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_stock_movements_tenant    ON stock_movements (tenant_id, created_at DESC);
CREATE INDEX idx_stock_movements_order     ON stock_movements (order_id)
    WHERE order_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- AUDIT_LOG
-- Consultas por entidade (ex: "todos os cancelamentos do pedido X")
-- Consultas por usuário (ex: "tudo que o usuário Y fez")
-- Consultas por período (ex: "ações das últimas 24h")
-- -----------------------------------------------------------------------------
CREATE INDEX idx_audit_log_tenant_created  ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_entity          ON audit_log (tenant_id, entity, entity_id);
CREATE INDEX idx_audit_log_user            ON audit_log (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_audit_log_action          ON audit_log (tenant_id, action, created_at DESC);

-- Para busca dentro do payload JSONB (útil para debugging e auditoria avançada)
CREATE INDEX idx_audit_log_payload         ON audit_log USING GIN (payload);

-- =============================================================================
-- RESUMO DOS PARTIAL INDEXES
-- Partial indexes reduzem o tamanho do índice filtrando apenas linhas relevantes.
-- Ex: idx_products_tenant_id só indexa produtos ativos e não-deletados,
-- que são os únicos consultados pelo PDV em runtime.
-- =============================================================================
