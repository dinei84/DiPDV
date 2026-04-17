# Modelagem do Banco de Dados — DiPDV

> PostgreSQL 16 — Shared Schema Multi-tenant com Row Level Security (RLS)

---

## Decisões de Design

| Decisão | Escolha | Justificativa |
|---|---|---|
| Estratégia multi-tenant | Shared Schema + RLS | Operação simples, migrations únicas, isolamento no nível do banco |
| Chave primária | UUID (`gen_random_uuid()`) | Sem exposição de sequência, seguro para SaaS multi-tenant |
| Soft delete | Apenas em `users` e `products` | Rastreabilidade de auditoria e integridade referencial histórica |
| Optimistic Locking | Campo `version` em `orders` | Previne edição simultânea sem bloquear o banco |
| Idempotência | `idempotency_key` em `payments` | Previne cobrança duplicada em retentativas Pix/TEF |
| Preços históricos | Campos `unit_price` em `order_items` | Preço congelado no momento da venda — nunca referenciar produto atual |
| Auditoria | Tabela `audit_log` imutável (sem UPDATE/DELETE) | Rastreabilidade completa de ações críticas |

---

## ENUMs PostgreSQL

Criados como `TYPE` no banco — mais performático que `VARCHAR + CHECK`.
Mapeados no Spring Boot com `@Enumerated(EnumType.STRING)`.

```sql
-- Perfis de acesso
user_role: ADMIN | MANAGER | CASHIER

-- Ciclo de vida do pedido
order_status: OPEN | CLOSED | CANCELED

-- Formas de pagamento
payment_method: CASH | CARD | PIX

-- Ciclo de vida do pagamento
payment_status: PENDING | PAID | FAILED | CANCELED | REFUNDED

-- Movimentações de caixa
cash_movement_type: SUPPLY | BLEEDING

-- Status do caixa
cash_register_status: OPEN | CLOSED

-- Movimentações de estoque
stock_movement_type: ENTRY | LOSS | SALE
```

---

## Diagrama de Entidades

### Domínio Principal (Produtos e Pedidos)

```
TENANTS ──────────────────────────────────────────────────────────┐
  │                                                                │
  ├──▶ USERS (role: ADMIN|MANAGER|CASHIER)                        │
  │                                                                │
  ├──▶ CATEGORIES                                                  │
  │         │                                                      │
  │         └──▶ PRODUCTS (stock_quantity, stock_min_level)        │
  │                   │                                            │
  │                   └──▶ PRODUCT_MODIFIER_GROUPS (N:N)           │
  │                                    │                           │
  ├──▶ MODIFIER_GROUPS ────────────────┘                          │
  │         └──▶ MODIFIER_OPTIONS                                  │
  │                                                                │
  ├──▶ ORDERS (version: Optimistic Lock)                           │
  │         │                                                      │
  │         ├──▶ ORDER_ITEMS (unit_price congelado)                │
  │         │         └──▶ ORDER_ITEM_MODIFIERS (price congelado)  │
  │         │                                                      │
  │         └──▶ PAYMENTS (idempotency_key)                        │
  │                                                                │
  └──────────────────────────────────────────────────────────────┘
```

### Operações (Caixa, Estoque, Auditoria)

```
TENANTS
  │
  ├──▶ CASH_REGISTERS (opened_by, closed_by → USERS)
  │         └──▶ CASH_MOVEMENTS (SUPPLY | BLEEDING)
  │         └──▶ ORDERS (cash_register_id)
  │
  ├──▶ STOCK_MOVEMENTS (product_id, order_id, type: ENTRY|LOSS|SALE)
  │
  └──▶ AUDIT_LOG (imutável: sem UPDATE/DELETE)
```

---

## Tabelas — Referência Completa

### `tenants`
Raiz da hierarquia multi-tenant. Sem RLS (tabela administrativa).

| Coluna | Tipo | Constraints |
|---|---|---|
| id | UUID PK | gen_random_uuid() |
| name | VARCHAR(120) | NOT NULL |
| slug | VARCHAR(60) | NOT NULL UNIQUE |
| active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

---

### `users`
Soft delete via `deleted_at`. Senha em BCrypt. Role via ENUM.

| Coluna | Tipo | Constraints |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK | → tenants |
| email | VARCHAR(180) | UNIQUE por tenant |
| password_hash | VARCHAR(255) | BCrypt fator 12 |
| name | VARCHAR(120) | |
| role | user_role | DEFAULT 'CASHIER' |
| active | BOOLEAN | DEFAULT TRUE |
| deleted_at | TIMESTAMPTZ | Soft delete |
| created_at / updated_at | TIMESTAMPTZ | |

**RLS:** `tenant_id = current_setting('app.current_tenant')::UUID`

---

### `categories`
Categorias do cardápio (Lanches, Bebidas, Combos...).

| Coluna | Tipo | Constraints |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK | → tenants |
| name | VARCHAR(80) | UNIQUE por tenant |
| active | BOOLEAN | DEFAULT TRUE |
| position | SMALLINT | Ordem no PDV |

---

### `products`
Itens do cardápio. Soft delete. Estoque simplificado no MVP.

| Coluna | Tipo | Constraints |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK | → tenants |
| category_id | UUID FK | → categories (ON DELETE SET NULL) |
| name | VARCHAR(120) | UNIQUE por tenant |
| description | TEXT | |
| price | NUMERIC(10,2) | CHECK >= 0 |
| stock_quantity | INTEGER | CHECK >= 0 |
| stock_min_level | INTEGER | Alerta abaixo deste valor |
| active | BOOLEAN | DEFAULT TRUE |
| deleted_at | TIMESTAMPTZ | Soft delete |

> **Evolução planejada (v2):** Substituir `stock_quantity` direto por tabela de receitas (`ingredients`) com fator de conversão por produto.

---

### `modifier_groups` + `modifier_options`
Grupos reutilizáveis entre produtos. Ex: "Ponto da carne" (min:1, max:1).

| modifier_groups | Tipo | Constraints |
|---|---|---|
| min_select / max_select | SMALLINT | CHECK min <= max |

| modifier_options | Tipo | Constraints |
|---|---|---|
| price_addition | NUMERIC(10,2) | DEFAULT 0, CHECK >= 0 |

---

### `orders`
Pedido do PDV. Campo `version` gerenciado pelo JPA (`@Version`).

| Coluna | Tipo | Observação |
|---|---|---|
| status | order_status | OPEN → CLOSED ou CANCELED |
| total | NUMERIC(10,2) | Calculado e atualizado a cada item adicionado |
| cancel_reason | TEXT | Obrigatório quando status = CANCELED (CHECK constraint) |
| version | INTEGER | Optimistic Locking — HTTP 409 em conflito |
| cash_register_id | UUID FK | Caixa aberto no momento da venda |

---

### `order_items` + `order_item_modifiers`

> ⚠️ **Preços são congelados no momento da venda.** Nunca recalcular com o preço atual do produto.

| Coluna | Observação |
|---|---|
| unit_price | Snapshot do preço do produto na venda |
| total_price | unit_price × quantity + soma dos modificadores |
| order_item_modifiers.price_addition | Snapshot do acréscimo do modificador |
| order_item_modifiers.name | Snapshot do nome do modificador |

---

### `payments`
Suporta pagamento misto (parte dinheiro + parte Pix no mesmo pedido).

| Coluna | Tipo | Observação |
|---|---|---|
| method | payment_method | CASH \| CARD \| PIX |
| status | payment_status | PENDING → PAID \| FAILED \| CANCELED |
| idempotency_key | VARCHAR(64) | UNIQUE por tenant — gerado no frontend |
| change_amount | NUMERIC(10,2) | Troco (apenas para CASH) |
| gateway_ref | VARCHAR(120) | txid Pix, NSU TEF |

---

### `cash_registers`
Um caixa por turno por tenant. Constraint especial impede dois caixas abertos:

```sql
CONSTRAINT chk_one_open_register_per_tenant
    EXCLUDE USING btree (tenant_id WITH =)
    WHERE (status = 'OPEN')
```

| Coluna | Observação |
|---|---|
| physical_balance | Informado pelo operador no fechamento |
| closing_balance | Calculado pelo sistema |
| difference | physical_balance - closing_balance |
| total_cash / total_card / total_pix | Totalizadores por forma de pagamento |

---

### `audit_log`
Imutável. `REVOKE UPDATE, DELETE ON audit_log FROM dipdv_app`.

| Coluna | Tipo | Observação |
|---|---|---|
| action | VARCHAR(60) | Constante: ORDER_CANCELED, CASH_REGISTER_CLOSED... |
| entity | VARCHAR(60) | Nome da tabela: orders, cash_registers... |
| entity_id | UUID | ID do registro afetado |
| payload | JSONB | `{"before": {...}, "after": {...}}` |
| ip_address | INET | IP do operador (extraído do request HTTP) |

---

## Row Level Security (RLS)

### Como funciona

```
Request HTTP
    │
    ▼
JwtAuthFilter → extrai tenant_id do JWT
    │
    ▼
TenantFilter → abre transação + executa:
    SET LOCAL app.current_tenant = '<tenant_uuid>';
    │
    ▼
PostgreSQL RLS Policy → filtra automaticamente:
    USING (tenant_id = current_setting('app.current_tenant', true)::UUID)
```

### Tabelas com RLS direto (tenant_id na própria tabela)
`users`, `categories`, `products`, `modifier_groups`, `cash_registers`, `orders`, `payments`, `stock_movements`, `audit_log`

### Tabelas com RLS indireto (herdam via FK)
| Tabela | Herda de |
|---|---|
| `modifier_options` | `modifier_groups.tenant_id` |
| `product_modifier_groups` | `products.tenant_id` |
| `cash_movements` | `cash_registers.tenant_id` |
| `order_items` | `orders.tenant_id` |
| `order_item_modifiers` | `order_items` → `orders.tenant_id` |

---

## Migrations Flyway

| Arquivo | Conteúdo |
|---|---|
| `V1__initial_schema.sql` | ENUMs, todas as tabelas e constraints |
| `V2__rls_policies.sql` | Role da aplicação, ENABLE RLS, policies |
| `V3__indexes.sql` | Todos os índices (incluindo partial indexes) |

**Convenção de nomenclatura para futuras migrations:**
```
V{N}__{descricao_snake_case}.sql
Ex: V4__add_discount_to_products.sql
    V5__create_notifications_table.sql
```

> ⚠️ **Nunca editar uma migration já executada em produção.** Sempre criar uma nova migration para corrigir ou alterar.

---

## Índices Notáveis

```sql
-- Partial index: apenas produtos ativos visíveis no PDV
CREATE INDEX idx_products_tenant_id ON products (tenant_id)
    WHERE active = TRUE AND deleted_at IS NULL;

-- Partial index: apenas pagamentos confirmados nos relatórios
CREATE INDEX idx_payments_tenant_method ON payments (tenant_id, method, created_at DESC)
    WHERE status = 'PAID';

-- GIN no JSONB do audit_log para buscas avançadas
CREATE INDEX idx_audit_log_payload ON audit_log USING GIN (payload);
```
