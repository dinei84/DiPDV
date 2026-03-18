-- =============================================================================
-- DiPDV — V2__rls_policies.sql
-- Row Level Security: isolamento total de dados entre tenants
-- =============================================================================
--
-- COMO FUNCIONA O RLS NO DIPDV:
-- 1. O Spring Boot possui um TenantFilter (Servlet Filter) que executa ANTES
--    de cada request HTTP.
-- 2. O Filter extrai o tenant_id do JWT (claim "tenantId").
-- 3. A cada transação, o Spring executa:
--      SET LOCAL app.current_tenant = '<tenant_uuid>';
--    Isso fica visível APENAS na transação atual — seguro para pool de conexões.
-- 4. As policies abaixo leem esse valor com:
--      current_setting('app.current_tenant', true)::UUID
--    O segundo argumento 'true' faz retornar NULL em vez de erro se não estiver
--    definido (importante para migrations e scripts admin que rodam sem tenant).
--
-- REFERÊNCIA SPRING BOOT (implementar em TenantFilter.java):
--    entityManager.createNativeQuery(
--        "SET LOCAL app.current_tenant = :tenantId"
--    ).setParameter("tenantId", tenantId.toString()).executeUpdate();
--
-- IMPORTANTE: O usuário da aplicação NÃO deve ser superuser.
--    Superusers bypassam o RLS. Crie um role dedicado (ver abaixo).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Role da aplicação
-- Criar antes de habilitar RLS. Superuser bypassa as policies.
-- Em produção: CREATE USER dipdv_app WITH PASSWORD 'senha-segura';
-- Aqui apenas garantimos que o role não é superuser.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'dipdv_app') THEN
        CREATE ROLE dipdv_app LOGIN;
    END IF;
END
$$;

-- Permissões da aplicação (apenas DML, nunca DDL)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO dipdv_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO dipdv_app;

-- Permissões para futuros objetos criados por migrations
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO dipdv_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO dipdv_app;

-- Revogar UPDATE e DELETE do audit_log para o role da aplicação
-- O INSERT é feito via @Aspect — nunca expor UPDATE/DELETE
REVOKE UPDATE, DELETE ON audit_log FROM dipdv_app;

-- -----------------------------------------------------------------------------
-- Habilitar RLS nas tabelas de negócio
-- tenants NÃO tem RLS — é a tabela raiz, acessada apenas por admin/sistema
-- -----------------------------------------------------------------------------

ALTER TABLE users                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories             ENABLE ROW LEVEL SECURITY;
ALTER TABLE products               ENABLE ROW LEVEL SECURITY;
ALTER TABLE modifier_groups        ENABLE ROW LEVEL SECURITY;
ALTER TABLE modifier_options       ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_modifier_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_registers         ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_movements         ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items            ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_item_modifiers   ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments               ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements        ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log              ENABLE ROW LEVEL SECURITY;

-- Forçar RLS mesmo para o owner da tabela (proteção adicional)
ALTER TABLE users                  FORCE ROW LEVEL SECURITY;
ALTER TABLE categories             FORCE ROW LEVEL SECURITY;
ALTER TABLE products               FORCE ROW LEVEL SECURITY;
ALTER TABLE modifier_groups        FORCE ROW LEVEL SECURITY;
ALTER TABLE modifier_options       FORCE ROW LEVEL SECURITY;
ALTER TABLE product_modifier_groups FORCE ROW LEVEL SECURITY;
ALTER TABLE cash_registers         FORCE ROW LEVEL SECURITY;
ALTER TABLE cash_movements         FORCE ROW LEVEL SECURITY;
ALTER TABLE orders                 FORCE ROW LEVEL SECURITY;
ALTER TABLE order_items            FORCE ROW LEVEL SECURITY;
ALTER TABLE order_item_modifiers   FORCE ROW LEVEL SECURITY;
ALTER TABLE payments               FORCE ROW LEVEL SECURITY;
ALTER TABLE stock_movements        FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log              FORCE ROW LEVEL SECURITY;

-- -----------------------------------------------------------------------------
-- POLICIES — tabelas com tenant_id direto
-- Padrão: USING filtra leitura, WITH CHECK filtra escrita
-- -----------------------------------------------------------------------------

-- USERS
CREATE POLICY tenant_isolation ON users
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- CATEGORIES
CREATE POLICY tenant_isolation ON categories
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- PRODUCTS
CREATE POLICY tenant_isolation ON products
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- MODIFIER_GROUPS
CREATE POLICY tenant_isolation ON modifier_groups
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- CASH_REGISTERS
CREATE POLICY tenant_isolation ON cash_registers
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- ORDERS
CREATE POLICY tenant_isolation ON orders
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- PAYMENTS
CREATE POLICY tenant_isolation ON payments
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- STOCK_MOVEMENTS
CREATE POLICY tenant_isolation ON stock_movements
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- AUDIT_LOG
CREATE POLICY tenant_isolation ON audit_log
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- -----------------------------------------------------------------------------
-- POLICIES — tabelas sem tenant_id direto (join com tabela pai)
-- Essas tabelas herdam o isolamento via FK para uma tabela com tenant_id
-- -----------------------------------------------------------------------------

-- MODIFIER_OPTIONS — herdado via modifier_group_id → modifier_groups.tenant_id
CREATE POLICY tenant_isolation ON modifier_options
    USING (
        EXISTS (
            SELECT 1 FROM modifier_groups mg
            WHERE mg.id = modifier_options.modifier_group_id
              AND mg.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM modifier_groups mg
            WHERE mg.id = modifier_options.modifier_group_id
              AND mg.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    );

-- PRODUCT_MODIFIER_GROUPS — herdado via product_id → products.tenant_id
CREATE POLICY tenant_isolation ON product_modifier_groups
    USING (
        EXISTS (
            SELECT 1 FROM products p
            WHERE p.id = product_modifier_groups.product_id
              AND p.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM products p
            WHERE p.id = product_modifier_groups.product_id
              AND p.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    );

-- CASH_MOVEMENTS — herdado via cash_register_id → cash_registers.tenant_id
CREATE POLICY tenant_isolation ON cash_movements
    USING (
        EXISTS (
            SELECT 1 FROM cash_registers cr
            WHERE cr.id = cash_movements.cash_register_id
              AND cr.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM cash_registers cr
            WHERE cr.id = cash_movements.cash_register_id
              AND cr.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    );

-- ORDER_ITEMS — herdado via order_id → orders.tenant_id
CREATE POLICY tenant_isolation ON order_items
    USING (
        EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = order_items.order_id
              AND o.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = order_items.order_id
              AND o.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    );

-- ORDER_ITEM_MODIFIERS — herdado via order_item_id → order_items → orders.tenant_id
CREATE POLICY tenant_isolation ON order_item_modifiers
    USING (
        EXISTS (
            SELECT 1 FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.id = order_item_modifiers.order_item_id
              AND o.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.id = order_item_modifiers.order_item_id
              AND o.tenant_id = current_setting('app.current_tenant', true)::UUID
        )
    );

-- =============================================================================
-- NOTA SOBRE PERFORMANCE DO RLS
-- As policies com EXISTS + JOIN nas tabelas filhas (order_items, etc.)
-- são mais custosas. Os índices em V3__indexes.sql cobrem exatamente esses
-- campos para garantir que o planner use index scan e não seq scan.
-- =============================================================================
