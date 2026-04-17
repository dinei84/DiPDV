ALTER TABLE payments
    ADD COLUMN cash_register_id UUID;

UPDATE payments p
SET cash_register_id = o.cash_register_id
FROM orders o
WHERE o.id = p.order_id
  AND p.cash_register_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM payments
        WHERE cash_register_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Unable to backfill payments.cash_register_id because some rows do not reference an order cash register';
    END IF;
END $$;

ALTER TABLE payments
    ALTER COLUMN cash_register_id SET NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT payments_cash_register_id_fkey
        FOREIGN KEY (cash_register_id) REFERENCES cash_registers(id) ON DELETE RESTRICT;

CREATE INDEX idx_payments_cash_register_id
    ON payments (cash_register_id);
