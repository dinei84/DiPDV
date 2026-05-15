-- V14: Adiciona identifier em orders e validações de integridade
ALTER TABLE orders ADD COLUMN identifier VARCHAR(100) NULL;

-- Partial unique index: impede dois pedidos com o mesmo identificador (ex: Mesa 1)
-- abertos simultaneamente no mesmo tenant.
-- Permite múltiplos pedidos com identifier NULL (anônimos).
-- Permite reuso do identifier se os pedidos anteriores já estiverem CLOSED ou CANCELED.
CREATE UNIQUE INDEX uq_orders_identifier_active
    ON orders (tenant_id, identifier)
    WHERE identifier IS NOT NULL AND status = 'OPEN';
