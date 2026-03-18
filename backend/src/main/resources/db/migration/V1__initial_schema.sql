-- =============================================================================
-- DiPDV — V1__initial_schema.sql
-- Migration base: ENUMs, tabelas e constraints
-- Flyway executa em transação única — qualquer erro faz rollback total
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ENUMs
-- Criados como TYPE no PostgreSQL — mais performático que VARCHAR + CHECK
-- No Spring Boot mapeados com @Enumerated(EnumType.STRING) nas entidades
-- -----------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM (
    'ADMIN',      -- acesso total ao tenant
    'MANAGER',    -- relatórios, caixa, estoque
    'CASHIER'     -- apenas PDV e abertura de caixa
);

CREATE TYPE order_status AS ENUM (
    'OPEN',       -- pedido em andamento
    'CLOSED',     -- pedido finalizado e pago
    'CANCELED'    -- cancelado pelo operador
);

CREATE TYPE payment_method AS ENUM (
    'CASH',       -- dinheiro
    'CARD',       -- cartão débito/crédito (TEF)
    'PIX'         -- Pix
);

CREATE TYPE payment_status AS ENUM (
    'PENDING',    -- aguardando confirmação do gateway
    'PAID',       -- confirmado
    'FAILED',     -- erro no processamento
    'CANCELED',   -- cancelado pelo operador
    'REFUNDED'    -- estornado (pós-MVP, já modelado)
);

CREATE TYPE cash_movement_type AS ENUM (
    'SUPPLY',     -- suprimento: entrada manual de dinheiro no caixa
    'BLEEDING'    -- sangria: retirada manual de dinheiro do caixa
);

CREATE TYPE cash_register_status AS ENUM (
    'OPEN',
    'CLOSED'
);

CREATE TYPE stock_movement_type AS ENUM (
    'ENTRY',      -- entrada de mercadoria
    'LOSS',       -- perda/descarte
    'SALE'        -- abate automático por venda (gerado pelo sistema)
);

-- -----------------------------------------------------------------------------
-- TENANTS
-- Raiz de toda a hierarquia multi-tenant.
-- Um tenant = um estabelecimento (lanchonete).
-- tenant_id é propagado para TODAS as tabelas de negócio.
-- -----------------------------------------------------------------------------

CREATE TABLE tenants (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    slug       VARCHAR(60)  NOT NULL UNIQUE,  -- usado na URL e no contexto RLS
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  tenants      IS 'Cadastro de clientes do SaaS (estabelecimentos)';
COMMENT ON COLUMN tenants.slug IS 'Identificador único amigável usado no contexto de RLS';

-- -----------------------------------------------------------------------------
-- USERS
-- Usuários pertencem a um tenant. Soft delete via deleted_at.
-- Senha armazenada como hash bcrypt (nunca texto puro).
-- Role define permissões via Spring Security @PreAuthorize.
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    role          user_role    NOT NULL DEFAULT 'CASHIER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,                             -- soft delete
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email_tenant UNIQUE (tenant_id, email)
);

COMMENT ON COLUMN users.password_hash IS 'Hash bcrypt gerado pelo Spring Security';
COMMENT ON COLUMN users.deleted_at    IS 'Soft delete — usuário não é removido fisicamente por rastreabilidade de audit_log';

-- -----------------------------------------------------------------------------
-- CATEGORIES
-- Categorias do cardápio (ex: Lanches, Bebidas, Combos).
-- Simples: nome + ativo. Hard delete permitido se não tiver produtos vinculados.
-- -----------------------------------------------------------------------------

CREATE TABLE categories (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name      VARCHAR(80)  NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    position  SMALLINT     NOT NULL DEFAULT 0,   -- ordem de exibição no PDV
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_category_name_tenant UNIQUE (tenant_id, name)
);

-- -----------------------------------------------------------------------------
-- PRODUCTS
-- Itens do cardápio. Soft delete via deleted_at.
-- stock_quantity e stock_min_level cobrem o MVP de estoque simplificado.
-- Evolução futura: tabela de receitas/ingredientes substitui stock direto.
-- -----------------------------------------------------------------------------

CREATE TABLE products (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    category_id     UUID           REFERENCES categories(id) ON DELETE SET NULL,
    name            VARCHAR(120)   NOT NULL,
    description     TEXT,
    price           NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock_quantity  INTEGER        NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    stock_min_level INTEGER        NOT NULL DEFAULT 0 CHECK (stock_min_level >= 0),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,                           -- soft delete
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_name_tenant UNIQUE (tenant_id, name)
);

COMMENT ON COLUMN products.stock_quantity  IS 'MVP: estoque por produto direto. v2: substituir por receitas de ingredientes';
COMMENT ON COLUMN products.stock_min_level IS 'Abaixo desse valor o sistema emite alerta de estoque baixo';

-- -----------------------------------------------------------------------------
-- MODIFIER_GROUPS
-- Grupos de modificadores reutilizáveis entre produtos.
-- Ex: "Ponto da carne" (min:1, max:1), "Adicionais" (min:0, max:3)
-- -----------------------------------------------------------------------------

CREATE TABLE modifier_groups (
    id         UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID       NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name       VARCHAR(80) NOT NULL,
    min_select SMALLINT   NOT NULL DEFAULT 0 CHECK (min_select >= 0),
    max_select SMALLINT   NOT NULL DEFAULT 1 CHECK (max_select >= 1),
    active     BOOLEAN    NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_modifier_min_max CHECK (min_select <= max_select)
);

-- -----------------------------------------------------------------------------
-- MODIFIER_OPTIONS
-- Opções dentro de um grupo. Ex: "Ao ponto", "Bem passado", "Extra queijo"
-- -----------------------------------------------------------------------------

CREATE TABLE modifier_options (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    modifier_group_id UUID           NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    name              VARCHAR(80)    NOT NULL,
    price_addition    NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (price_addition >= 0),
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    position          SMALLINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- PRODUCT_MODIFIER_GROUPS
-- Tabela associativa N:N entre produtos e grupos de modificadores.
-- Um grupo pode ser reutilizado em vários produtos.
-- -----------------------------------------------------------------------------

CREATE TABLE product_modifier_groups (
    product_id        UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    modifier_group_id UUID NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    position          SMALLINT NOT NULL DEFAULT 0,

    PRIMARY KEY (product_id, modifier_group_id)
);

-- -----------------------------------------------------------------------------
-- CASH_REGISTERS
-- Representa um turno de caixa. Um caixa por turno por tenant (MVP).
-- Totais por forma de pagamento calculados no fechamento.
-- opened_by / closed_by rastreiam quem abriu e quem fechou.
-- -----------------------------------------------------------------------------

CREATE TABLE cash_registers (
    id               UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID                 NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    opened_by        UUID                 NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    closed_by        UUID                 REFERENCES users(id) ON DELETE RESTRICT,
    status           cash_register_status NOT NULL DEFAULT 'OPEN',
    opening_balance  NUMERIC(10, 2)       NOT NULL DEFAULT 0 CHECK (opening_balance >= 0),
    closing_balance  NUMERIC(10, 2),      -- saldo calculado pelo sistema no fechamento
    physical_balance NUMERIC(10, 2),      -- saldo físico informado pelo operador
    difference       NUMERIC(10, 2),      -- physical_balance - closing_balance
    total_cash       NUMERIC(10, 2)       NOT NULL DEFAULT 0,
    total_card       NUMERIC(10, 2)       NOT NULL DEFAULT 0,
    total_pix        NUMERIC(10, 2)       NOT NULL DEFAULT 0,
    opened_at        TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMPTZ,

    CONSTRAINT chk_one_open_register_per_tenant
        EXCLUDE USING btree (tenant_id WITH =)
        WHERE (status = 'OPEN')   -- garante no máximo 1 caixa aberto por tenant
);

COMMENT ON CONSTRAINT chk_one_open_register_per_tenant ON cash_registers
    IS 'Exclusion constraint: impede dois caixas abertos simultaneamente no mesmo tenant';

-- -----------------------------------------------------------------------------
-- CASH_MOVEMENTS
-- Entradas e saídas manuais durante o turno (sangria e suprimento).
-- Vinculadas ao caixa e ao usuário que as registrou.
-- -----------------------------------------------------------------------------

CREATE TABLE cash_movements (
    id               UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_register_id UUID               NOT NULL REFERENCES cash_registers(id) ON DELETE RESTRICT,
    user_id          UUID               NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type             cash_movement_type NOT NULL,
    amount           NUMERIC(10, 2)     NOT NULL CHECK (amount > 0),
    description      VARCHAR(255)       NOT NULL,
    created_at       TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- ORDERS
-- Pedido gerado no PDV. version = Optimistic Locking (gerenciado pelo JPA).
-- cash_register_id vincula o pedido ao turno de caixa ativo.
-- -----------------------------------------------------------------------------

CREATE TABLE orders (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cash_register_id UUID         REFERENCES cash_registers(id) ON DELETE RESTRICT,
    status           order_status NOT NULL DEFAULT 'OPEN',
    total            NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    cancel_reason    TEXT,        -- obrigatório quando status = CANCELED
    version          INTEGER      NOT NULL DEFAULT 0,   -- Optimistic Locking (@Version no JPA)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMPTZ,

    CONSTRAINT chk_cancel_reason CHECK (
        (status = 'CANCELED' AND cancel_reason IS NOT NULL)
        OR status != 'CANCELED'
    )
);

COMMENT ON COLUMN orders.version IS '@Version no JPA — incrementado a cada UPDATE; conflito gera OptimisticLockException → HTTP 409';

-- -----------------------------------------------------------------------------
-- ORDER_ITEMS
-- Itens de um pedido. unit_price e total_price são congelados no momento da
-- venda — nunca referenciar o preço atual do produto para fins históricos.
-- -----------------------------------------------------------------------------

CREATE TABLE order_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID           NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity    SMALLINT       NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price  NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),   -- congelado no momento da venda
    total_price NUMERIC(10, 2) NOT NULL CHECK (total_price >= 0),  -- unit_price * quantity + modificadores
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN order_items.unit_price  IS 'Preço congelado no momento da venda. Nunca atualizar retroativamente.';
COMMENT ON COLUMN order_items.total_price IS 'unit_price * quantity + soma dos price_addition dos modificadores escolhidos';

-- -----------------------------------------------------------------------------
-- ORDER_ITEM_MODIFIERS
-- Modificadores escolhidos para cada item do pedido.
-- price_addition congelado no momento da venda (igual a order_items.unit_price).
-- -----------------------------------------------------------------------------

CREATE TABLE order_item_modifiers (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id      UUID           NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    modifier_option_id UUID           NOT NULL REFERENCES modifier_options(id) ON DELETE RESTRICT,
    name               VARCHAR(80)    NOT NULL,           -- snapshot do nome no momento da venda
    price_addition     NUMERIC(10, 2) NOT NULL DEFAULT 0  -- snapshot do preço no momento da venda
);

-- -----------------------------------------------------------------------------
-- PAYMENTS
-- Transação de pagamento vinculada a um pedido.
-- idempotency_key previne duplicidade em retentativas de Pix/TEF.
-- Um pedido pode ter mais de um pagamento (ex: parte em dinheiro, parte no Pix).
-- -----------------------------------------------------------------------------

CREATE TABLE payments (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    order_id         UUID           NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    method           payment_method NOT NULL,
    status           payment_status NOT NULL DEFAULT 'PENDING',
    amount           NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    change_amount    NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (change_amount >= 0),  -- troco para CASH
    idempotency_key  VARCHAR(64)    NOT NULL,
    gateway_ref      VARCHAR(120),  -- referência externa do gateway (Pix txid, TEF nsu)
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payment_idempotency UNIQUE (tenant_id, idempotency_key)
);

COMMENT ON COLUMN payments.idempotency_key IS 'Gerado no frontend antes de enviar. Backend rejeita duplicata retornando o resultado original.';
COMMENT ON COLUMN payments.gateway_ref     IS 'Referência externa: txid do Pix, NSU do TEF, etc.';

-- -----------------------------------------------------------------------------
-- STOCK_MOVEMENTS
-- Histórico de movimentações de estoque.
-- SALE é gerado automaticamente pelo sistema ao fechar um pedido.
-- ENTRY e LOSS são registrados manualmente pelo gerente.
-- -----------------------------------------------------------------------------

CREATE TABLE stock_movements (
    id         UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID                NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    product_id UUID                NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    user_id    UUID                NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type       stock_movement_type NOT NULL,
    quantity   INTEGER             NOT NULL CHECK (quantity != 0),  -- positivo = entrada, negativo = saída
    reason     VARCHAR(255)        NOT NULL,
    order_id   UUID                REFERENCES orders(id) ON DELETE SET NULL,  -- preenchido quando type = SALE
    created_at TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN stock_movements.quantity IS 'Positivo = entrada (ENTRY), negativo = saída (LOSS, SALE)';
COMMENT ON COLUMN stock_movements.order_id IS 'Preenchido automaticamente quando type = SALE para rastrear qual venda originou o abate';

-- -----------------------------------------------------------------------------
-- AUDIT_LOG
-- Log imutável de ações críticas: cancelamento de pedido, fechamento de caixa,
-- ajuste de estoque, edição de produto.
-- Implementado via @Aspect (AOP) no Spring Boot — transparente para o Service.
-- NUNCA conceder UPDATE ou DELETE nesta tabela.
-- payload guarda snapshot JSON do estado anterior/novo.
-- -----------------------------------------------------------------------------

CREATE TABLE audit_log (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    user_id    UUID        REFERENCES users(id) ON DELETE SET NULL,  -- NULL se ação do sistema
    action     VARCHAR(60)  NOT NULL,   -- ex: ORDER_CANCELED, CASH_REGISTER_CLOSED, STOCK_ADJUSTED
    entity     VARCHAR(60)  NOT NULL,   -- ex: orders, cash_registers, products
    entity_id  UUID         NOT NULL,
    payload    JSONB        NOT NULL DEFAULT '{}',  -- snapshot: {before: {...}, after: {...}}
    ip_address INET,                    -- IP do operador (extraído do request no Spring)
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  audit_log         IS 'Log imutável. Nunca conceder UPDATE/DELETE. Implementado via @Aspect no Spring Boot.';
COMMENT ON COLUMN audit_log.action  IS 'Constantes em AuditAction enum: ORDER_CANCELED, CASH_REGISTER_CLOSED, etc.';
COMMENT ON COLUMN audit_log.payload IS 'Formato: {"before": {...}, "after": {...}} ou {"snapshot": {...}}';
