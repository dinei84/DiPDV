# PROJECT_STATE.md — DiPDV

> Estado atualizado em 09/05/2026 após auditoria completa do
> repositório. Documento substitui versões anteriores
> (que continham informações imprecisas sobre o estado do PDV).

---

## Stack consolidada

### Backend
- Java 21 + Spring Boot 3.3.x
- PostgreSQL 16 com Row-Level Security
- Flyway migrations: V1 → V11
- Spring Security + JWT
- AOP para feature gating (`@RequiresModule`)
- Testcontainers para testes IT

### Frontend PDV (`frontend/`)
- Next.js 16.2.0 + React 19.2.4 + TypeScript 5
- Tailwind v4 (raw, sem shadcn)
- localStorage para JWT (chaves: `dipdv_token`, `dipdv_user`)
- Porta 3000

### Frontend Admin (`admin/`)
- Mesma stack do PDV
- localStorage com chaves separadas (`dipdv_admin_token`, `dipdv_admin_user`)
- Porta 3001
- Apenas SUPER_ADMIN tem acesso

### Ambiente local
- Docker container `global_db` (postgres:16-alpine), porta 5432
- Backend: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- Frontend PDV: `cd frontend && npm run dev`
- Admin: `cd admin && npm run dev`

---

## Modelo de negócio

SaaS B2B multi-tenant com **feature gating por tenant**:

- **Tier base** (sempre liberado): `PDV_BASIC`, `CATALOG_MANAGEMENT`
- **Tier paid** (liberado pelo SUPER_ADMIN): `PAYMENT_PIX`, `PAYMENT_CARD`, `REPORTS`, `INVENTORY`, `WHATSAPP_ORDERS`, `IFOOD_INTEGRATION`, `LOYALTY`

Tenant nasce só com tier base + categoria default "Diversos".
SUPER_ADMIN libera o restante manualmente.

### Roles
- **SUPER_ADMIN**: cross-tenant. Gerencia tenants e módulos. Não opera PDV.
- **ADMIN**: dono/gerente do tenant. Acesso total dentro dos módulos contratados. Gerencia equipe.
- **MANAGER**: supervisor. Vê relatórios, abre/fecha caixa, cancela pedidos.
- **CASHIER**: operador. Lança pedidos, recebe pagamentos.

Roles fixas, sem matriz customizada.

---

## Estado atual por componente

### Backend — completo e estável

✅ **Auth** — login, JWT, role check via `@PreAuthorize`
✅ **Multi-tenancy** — RLS em todas as tabelas tenant
✅ **Feature gating** — `@RequiresModule` + ModuleGatingAspect, endpoints `/api/v1/admin/modules/*` e `/api/v1/me/modules`
✅ **Tenants** — CRUD completo `/api/v1/admin/tenants`, soft delete via `active=false`, proteção de tenant default e master
✅ **Catalog** — CRUD completo de produtos e categorias `/api/v1/categories`, `/api/v1/products`. Soft delete via `deletedAt`. Auto-criação de "Diversos" no `TenantAdminService.createTenant`. Suporte a `?includeDeleted=true`
✅ **Cash register** — endpoints implementados, **mas não consumidos pelo frontend**
✅ **Orders** — endpoints implementados, **mas não consumidos pelo frontend**
✅ **Payments** — implementação básica (cash + PIX mock), **não consumido pelo frontend**
✅ **Reports** — endpoints `/api/v1/reports/*`, consumidos pelo frontend
✅ **Audit log** — entidade existe, populada parcialmente

**Total de testes:** 178 verdes (estado atual de `develop`).

Comando: `mvn test -Dexclude.integration.tests=""`

### Admin (porta 3001) — completo

✅ Login + AdminGuard (apenas SUPER_ADMIN)
✅ Sistema de toast (Context + Provider, bottom-right, dedup, limite 3)
✅ Lista de tenants
✅ Criação de tenant (com auto-criação de categoria "Diversos")
✅ Edição de tenant (nome, slug, módulos, ativo/inativo)
✅ Toggle de módulos com feedback otimista
✅ Toggle de ativação/desativação com modal de confirmação
✅ Proteção de tenant default e master tenant contra desativação

### Frontend PDV (porta 3000) — apenas base

✅ Login
✅ AuthGuard (Client Component com isChecking)
✅ ModuleGate (esconde menus de módulos não contratados)
✅ ModuleNotAvailable (fallback de página)
✅ Hook `useModules` (cruza catalog + módulos do tenant)
✅ Sidebar simples no header (PDV, Relatórios, Sair)
✅ Tela de relatórios funcional + dashboard widget integrado

❌ **Tela de venda do PDV (catálogo + comanda)** — NÃO EXISTE
❌ **Modal de abertura/fechamento de caixa** — NÃO EXISTE
❌ **Fluxo de lançamento de pedido** — NÃO EXISTE
❌ **Fluxo de pagamento (cash, PIX)** — NÃO EXISTE
❌ **Tela de troco** — NÃO EXISTE
❌ **Sistema de toast** — NÃO EXISTE no PDV (existe no admin)
❌ **ApiError tipado** — NÃO EXISTE no PDV (existe no admin)
❌ **MoneyInput** — NÃO EXISTE
❌ **ConfirmDialog/Modal genérico** — NÃO EXISTE
❌ **Telas de gestão de produtos/categorias** — NÃO EXISTE
❌ **Gestão de funcionários** — NÃO EXISTE

**Importante:** versões anteriores deste documento listavam alguns desses como "implementado". Auditoria por `git log --all --diff-filter=A` confirma que nunca foram commitados ao repositório.

---

## Decisões de produto tomadas

### Identidade e roles
- Roles fixas (ADMIN/MANAGER/CASHIER), sem matriz customizada por usuário
- SUPER_ADMIN apenas no admin (3001), bloqueado no PDV
- User management dentro do tenant (ADMIN gerencia equipe) — **a implementar**

### Soft delete
- Tenants: via `active=false`
- Categorias: via `deletedAt` timestamp
- Produtos: via `deletedAt` timestamp
- Tenant default e master tenant não podem ser desativados

### Catálogo
- Produto pertence sempre a uma categoria (FK NOT NULL)
- Categoria default "Diversos" criada automaticamente para todo tenant
- "Diversos" é editável (nome, ícone) mas não excluível
- Categoria com produtos vinculados não pode ser excluída
- Sem variações (cada tamanho = produto separado)
- 12 ícones lucide-react disponíveis (definição em sprint frontend pendente)

### Distribuição de credenciais (decidida, não implementada)
- Modelo B: convite por link com token
- 30 dias de expiração + use-once
- Sem SMTP no MVP — link enviado manualmente por canal externo
- Implementação adiada até aparecer 2º tenant real

### Polimento já consolidado
- Cor de inputs do PDV — corrigido
- Hydration warning — corrigido com `suppressHydrationWarning`
- `addItem` 200 → 201 — corrigido
- `ip_address` em audit_log — corrigido (captura de IP via `HttpServletRequest`)

---

## Dívidas técnicas registradas

1. **`categories.position`** existe na tabela mas não é exposta nem manipulada — aguarda sprint de UX de catálogo (drag-and-drop ou campo numérico)
2. **`Product.stockQuantity`/`stockMinLevel`** voltaram à tabela mas pertencem ao módulo `INVENTORY` (PAID) — endpoints `/products/low-stock` anotados com `@RequiresModule("INVENTORY")`
3. **Integration tests não rodam em `mvn test` padrão** — exigem flag `-Dexclude.integration.tests=""`. Importante registrar para futuros agentes e pipelines de CI
4. **Tailwind v4 com `@config` legado** — recomendado migrar para `@theme` no CSS principal em sprint futura
5. **PDV: chaves de localStorage** documentadas (`dipdv_token`, `dipdv_user`) — diferentes do admin, intencional

---

## Roadmap reformulado

Estado: **backend muito à frente do frontend PDV.** Frontend tem login + relatórios e nada mais. PDV operacional inteiro a fazer.

### Frente A — Catálogo (em andamento)
- ✅ **4a.1** — Backend de catálogo (mergeada)
- ⏳ **4a.2** — Frontend de gestão de catálogo (`/manage/products`, `/manage/categories`) — **pausada para replanejamento**

### Frente B — PDV operacional (a fazer)
Esta é a maior frente. Várias sprints sequenciais:

- **4b** — Tela de venda do PDV: catálogo (consome `/api/v1/products`), comanda, lançamento de item
- **4c** — Fluxo de caixa: abertura, sangria/suprimento, fechamento, conferência
- **4d** — Pagamento: cash com troco, PIX (mock), múltiplos meios no mesmo pedido
- **4e** — Dashboard de pedidos: status (preparando/pronto/entregue), timer de atrasos
- **4f** — User management: ADMIN cadastra MANAGER/CASHIER

### Frente C — Pré-deploy
- Polimento de UX do fluxo completo
- Convite por link (Modelo B) — só quando 2º tenant aparecer
- Documentação operacional

### Frente D — Deploy
- Railway com banco de produção
- Variáveis de ambiente, CORS, domínio
- Smoke test em produção

---

## Credenciais de desenvolvimento

- Tenant default: `00000000-0000-0000-0000-000000000001`
- Master tenant: `ffffffff-ffff-ffff-ffff-ffffffffffff` (apenas para SUPER_ADMIN)
- `admin@dipdv.dev` / `dipdv@2025` (ADMIN do tenant default)
- `superadmin@dipdv.app` / `SuperAdmin@2025!` (SUPER_ADMIN, login no admin/3001)

---

## Como manter este arquivo

- Atualizar a cada sprint encerrada
- Estado real auditado periodicamente via `git log` + `find`
- Nunca registrar como ✅ algo que não foi commitado
