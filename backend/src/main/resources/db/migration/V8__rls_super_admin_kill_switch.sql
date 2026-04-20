-- =============================================================================
-- DiPDV — V8__rls_super_admin_kill_switch.sql
-- Adiciona Kill Switch duplo às políticas RLS existentes.
--
-- BYPASS exige AMBAS as condições simultaneamente:
--   app.is_super_admin = 'true'
--   app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
--
-- Cada condição sozinha NÃO concede bypass. Isso protege contra:
--   - Token com flag is_super_admin mas tenant UUID normal
--   - Token com master UUID mas sem a flag de superadmin
--
-- NOTA SOBRE NULLIF:
-- Após um SET LOCAL dentro de uma transação, ao fazer COMMIT o PostgreSQL
-- reverte a variável customizada para '' (string vazia), não NULL.
-- NULLIF(current_setting(..., true), '') converte '' para NULL antes do cast,
-- evitando "invalid input syntax for type uuid: ''" quando não há contexto ativo.
-- =============================================================================

-- Expressão auxiliar usada em todos os ALTER POLICY:
-- kill_switch  = (is_super_admin = 'true' AND current_tenant = master_uuid)
-- tenant_match = tenant_id = NULLIF(current_setting(...), '')::UUID

-- ─── Tabelas com tenant_id direto ────────────────────────────────────────────

ALTER POLICY tenant_isolation ON users
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON categories
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON products
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON modifier_groups
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON cash_registers
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON orders
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON payments
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON stock_movements
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

ALTER POLICY tenant_isolation ON audit_log
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
    );

-- ─── Tabelas com tenant_id indireto (via JOIN) ───────────────────────────────

ALTER POLICY tenant_isolation ON modifier_options
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM modifier_groups mg
            WHERE mg.id = modifier_options.modifier_group_id
              AND mg.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM modifier_groups mg
            WHERE mg.id = modifier_options.modifier_group_id
              AND mg.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    );

ALTER POLICY tenant_isolation ON product_modifier_groups
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM products p
            WHERE p.id = product_modifier_groups.product_id
              AND p.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM products p
            WHERE p.id = product_modifier_groups.product_id
              AND p.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    );

ALTER POLICY tenant_isolation ON cash_movements
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM cash_registers cr
            WHERE cr.id = cash_movements.cash_register_id
              AND cr.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM cash_registers cr
            WHERE cr.id = cash_movements.cash_register_id
              AND cr.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    );

ALTER POLICY tenant_isolation ON order_items
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = order_items.order_id
              AND o.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM orders o
            WHERE o.id = order_items.order_id
              AND o.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    );

ALTER POLICY tenant_isolation ON order_item_modifiers
    USING (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.id = order_item_modifiers.order_item_id
              AND o.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    )
    WITH CHECK (
        (current_setting('app.is_super_admin', true) = 'true'
         AND current_setting('app.current_tenant', true) = 'ffffffff-ffff-ffff-ffff-ffffffffffff')
        OR EXISTS (
            SELECT 1 FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.id = order_item_modifiers.order_item_id
              AND o.tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::UUID
        )
    );
