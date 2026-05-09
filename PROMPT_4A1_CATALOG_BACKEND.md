# Prompt — Sprint 4a.1: Backend de Categorias e Produtos

Branch: `feature/catalog-backend` (a partir de `develop`).

Pré-requisito: Frente 1 (limpeza de dívidas) já mergeada em
`develop`.

---

## Contexto

Tenant precisa cadastrar próprio catálogo de produtos. Hoje
existe seed de produtos no `DataInitializer`, mas não há
endpoints CRUD. Esta sprint cobre **apenas o backend** —
frontend vem depois.

---

## Workflow

**Fase 0 — Investigação curta:**
- Confirmar estado atual das tabelas `products` e `categories`
  (existem? quais colunas?). Reportar.
- Confirmar onde estão `MasterTenantConstants` e
  `DEFAULT_TENANT_ID` (vão ser referenciados na migration).
- Confirmar padrão de testes IT (provavelmente
  `TenantAdminControllerIT` é a melhor referência).
- Reportar antes de codificar.

**Fase 1 — Implementação:** após aprovação, commits atômicos.

**Build mandatório:** `mvn test` com todos os testes verdes.

---

## Schema (Migration nova — V12 ou próxima disponível)

### Tabela `categories`

Se a tabela já existir, **alterar** com `ALTER TABLE` para
adicionar colunas faltantes. Se não existir, criar:

```
id           UUID PRIMARY KEY
tenant_id    UUID NOT NULL
name         VARCHAR(100) NOT NULL
icon         VARCHAR(50) NOT NULL DEFAULT 'package'
active       BOOLEAN NOT NULL DEFAULT true
is_default   BOOLEAN NOT NULL DEFAULT false
created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()

UNIQUE (tenant_id, name)  -- nome único dentro do tenant
RLS habilitado, mesma política usada em outras tabelas tenant
```

### Tabela `products`

Provavelmente já existe (foi seedada). Confirmar e ajustar:

```
id           UUID PRIMARY KEY
tenant_id    UUID NOT NULL
category_id  UUID NOT NULL REFERENCES categories(id)
name         VARCHAR(100) NOT NULL
price        NUMERIC(10,2) NOT NULL CHECK (price >= 0)
active       BOOLEAN NOT NULL DEFAULT true
created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()

RLS habilitado
```

Se `category_id` não existir hoje, adicionar como NOT NULL
exige preencher antes — ver Backfill abaixo.

### Backfill — categoria default "Diversos"

Para todos os tenants existentes em `tenants`:
1. Inserir categoria `name='Diversos'`, `icon='package'`,
   `active=true`, `is_default=true`
2. Atualizar produtos seedados sem categoria para apontar pra
   essa nova categoria (`category_id = id da Diversos do
   tenant`)

Para tenants criados a partir desta migration: a criação da
"Diversos" deve acontecer no momento da criação do tenant —
modificar `TenantAdminService.createTenant` (ou onde tenants
nascem) para auto-criar a categoria default em sequência.

---

## Endpoints

Sob `/api/v1`. Todos exigem usuário autenticado. Acesso de
**escrita** (POST/PUT/DELETE) restrito a `ADMIN` do tenant.
Leitura (GET) liberada para qualquer role autenticado.

Anotar todos com `@RequiresModule("CATALOG_MANAGEMENT")` para
manter o padrão (módulo BASE, sempre liberado mas explícito).

### Categorias

| Método | Path | Função |
|---|---|---|
| GET | `/categories` | Lista categorias do tenant logado |
| POST | `/categories` | Cria categoria (apenas ADMIN) |
| PUT | `/categories/{id}` | Atualiza categoria (apenas ADMIN) |
| DELETE | `/categories/{id}` | Hard delete (apenas ADMIN) |

**Regras de negócio (no service):**
- POST: nome único dentro do tenant. Conflito → 409.
- PUT: pode editar nome e ícone. Tentar mudar `is_default` é
  ignorado silenciosamente (não retorna erro, mas não aplica).
- DELETE:
  - Se `is_default=true` → 400 com mensagem `"Categoria padrão
    não pode ser excluída"`
  - Se há **qualquer produto vinculado** (mesmo inativos) →
    400 com mensagem `"Categoria não pode ser excluída pois
    possui produtos vinculados"`
  - Caso contrário, hard delete

### Produtos

| Método | Path | Função |
|---|---|---|
| GET | `/products` | Lista produtos do tenant logado |
| POST | `/products` | Cria produto (apenas ADMIN) |
| PUT | `/products/{id}` | Atualiza produto (apenas ADMIN) |
| DELETE | `/products/{id}` | Soft delete (apenas ADMIN) |

**Regras de negócio (no service):**
- POST: requer `name`, `price`, `category_id`. Categoria deve
  existir e pertencer ao mesmo tenant. Caso contrário → 400.
- POST: nome único dentro do tenant (case-insensitive recomendado).
  Conflito → 409.
- PUT: pode editar `name`, `price`, `category_id`, `active`.
  Mesmas validações do POST sobre categoria existente.
- DELETE: soft delete (`active=false`). Não remove do banco.
  Reativar é via PUT com `active=true`.
- GET: por padrão lista TODOS (ativos e inativos). Aceitar query
  param `?activeOnly=true` para filtrar apenas ativos (será útil
  pra listagem do PDV de venda na sprint futura).

---

## DTOs (records Java)

### Categorias

```java
record CategoryRequest(
  String name,
  String icon
)

record CategoryResponse(
  UUID id,
  String name,
  String icon,
  boolean active,
  boolean isDefault,
  long productCount,   // util pra UI saber se pode excluir
  Instant createdAt
)
```

### Produtos

```java
record ProductRequest(
  String name,
  BigDecimal price,
  UUID categoryId,
  Boolean active   // opcional no PUT, ignorado no POST (sempre true)
)

record ProductResponse(
  UUID id,
  String name,
  BigDecimal price,
  UUID categoryId,
  String categoryName,   // join na resposta para evitar N+1 no frontend
  String categoryIcon,
  boolean active,
  Instant createdAt
)
```

---

## Testes IT

Adicionar arquivo `CategoryControllerIT.java` e
`ProductControllerIT.java`. Mínimo de testes:

### Categorias (≥ 7 testes)
1. ADMIN lista categorias do tenant → 200, contém pelo menos
   "Diversos"
2. CASHIER lista categorias → 200 (leitura permitida)
3. ADMIN cria categoria com nome único → 201, productCount=0
4. ADMIN cria categoria com nome duplicado → 409
5. CASHIER tenta criar categoria → 403
6. ADMIN tenta excluir categoria default → 400 com mensagem
   específica
7. ADMIN tenta excluir categoria com produtos vinculados → 400
   com mensagem específica
8. ADMIN exclui categoria vazia (sem produtos, não-default) →
   204

### Produtos (≥ 7 testes)
1. ADMIN cria produto válido → 201
2. ADMIN cria produto com category_id de outro tenant → 400
   (RLS deve impedir mesmo, mas service valida)
3. ADMIN cria produto sem categoria → 400 (`categoryId` null)
4. ADMIN cria produto com nome duplicado → 409
5. ADMIN edita produto → 200, mudanças persistidas
6. ADMIN deleta produto → 204, banco mostra `active=false` (não
   removido)
7. GET com `?activeOnly=true` retorna apenas ativos
8. CASHIER tenta criar produto → 403

Total esperado: 164 testes atuais + ~16 novos = **~180 testes
verdes**.

---

## Princípios

- **Não criar telas frontend.** Apenas backend nesta sprint.
- **Não tocar em outros endpoints/serviços** que não os listados.
- **Reportar bugs adicionais** que descobrir, sem corrigir.
- **Build mandatório** antes de declarar concluído.
- **Sem simular validação.** Backend é validável via testes IT;
  isso é suficiente para esta sprint.

---

## Relatório esperado

**Fase 0:**
- Estado atual de `products` e `categories`
- Localização dos constants (`DEFAULT_TENANT_ID`)
- Confirmação de padrão de testes IT a seguir
- Estratégia de backfill confirmada (especialmente se `products`
  já tem dados sem categoria)

**Fase 1:**
- Lista de arquivos criados/alterados
- Migration nova: número da versão
- Resultado de `mvn test` verbatim
- Total de testes: 164 → ?
- Cobertura mínima dos cenários listados (✓ por cenário)
- Desvios da especificação, se houver
