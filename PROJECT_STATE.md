# PROJECT_STATE.md — DiPDV

> Estado atualizado em 14/05/2026 após encerramento da Sprint 4a.2
> (Frontend de gestão de catálogo). Frente A concluída.

---

## Stack consolidada

### Backend
- Java 21 + Spring Boot 3.3.x
- PostgreSQL 16 com Row-Level Security
- Flyway migrations: V1 → V13
- Spring Security + JWT
- AOP para feature gating (`@RequiresModule`)
- Testcontainers para testes IT
- **Total atual de testes: 185 verdes** (`mvn test -Dexclude.integration.tests=""`)

### Frontend PDV (`frontend/`)
- Next.js 16.2.0 + React 19.2.4 + TypeScript 5
- Tailwind v4 (raw, sem shadcn)
- lucide-react para ícones
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
- **ADMIN**: dono/gerente do tenant. Acesso total dentro dos módulos contratados. Gerencia equipe e catálogo.
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
✅ **Catalog** — CRUD completo de produtos e categorias. Soft delete via `deletedAt`. Auto-criação de "Diversos". Suporte a `?includeDeleted=true` e `?categoryId=UUID`. Endpoints `PATCH /{id}/reactivate` em ambos com tratamento de 409 (conflito de nome). Categorias com `position` exposto, listagem ordenada por `position ASC, name ASC`. **Partial unique index** em ambos via V12 (categorias) e V13 (produtos): `WHERE deleted_at IS NULL` — permite múltiplos inativos com mesmo nome, apenas um ativo.
✅ **Cash register** — endpoints implementados, **mas não consumidos pelo frontend**
✅ **Orders** — endpoints implementados, **mas não consumidos pelo frontend**
✅ **Payments** — implementação básica (cash + PIX mock), **não consumido pelo frontend**
✅ **Reports** — endpoints `/api/v1/reports/*`, consumidos pelo frontend
✅ **Audit log** — entidade existe, populada parcialmente

**Total de testes:** 185 verdes (`mvn test -Dexclude.integration.tests=""`)

### Admin (porta 3001) — completo

✅ Login + AdminGuard (apenas SUPER_ADMIN)
✅ Sistema de toast (Context + Provider, bottom-right, dedup, limite 3)
✅ Lista de tenants
✅ Criação de tenant (com auto-criação de categoria "Diversos")
✅ Edição de tenant (nome, slug, módulos, ativo/inativo)
✅ Toggle de módulos com feedback otimista
✅ Toggle de ativação/desativação com modal de confirmação
✅ Proteção de tenant default e master tenant contra desativação

### Frontend PDV (porta 3000) — base + catálogo completo

**Infra estabelecida (battle-tested pela Sprint 4a.2):**
✅ Login + AuthGuard (Client Component com isChecking)
✅ ModuleGate (esconde menus de módulos não contratados)
✅ ModuleNotAvailable (fallback de página)
✅ Hook `useModules`
✅ Sistema de Toast (Context + Provider, bottom-right, dedup, limite 3, API imperativa)
✅ ApiError tipado (`src/lib/api-error.ts`) + wrapper de fetch com interceptação global de 401/403 → toast automático
✅ ConfirmDialog Promise-based (`src/lib/confirm/`)
✅ MoneyInput (`src/components/MoneyInput.tsx`) — estado em centavos, display BRL
✅ Helpers de preço (`src/lib/price.ts`) — `centsToBRL`, `apiPriceToCents`, `centsToApiString`
✅ Tipo `Page<T>` reutilizável + `Category`/`Product` + DTOs em `src/lib/types.ts`
✅ Header com grupo "Gestão" (dropdown) visível apenas para ADMIN

**Telas implementadas:**
✅ Login
✅ Relatórios + dashboard widget
✅ `/manage/categories` — lista + drawer, 12 ícones lucide, campo "Ordem de exibição", toggle "Ver inativos", Desativar/Reativar com tratamento de 409
✅ `/manage/products` — lista + drawer, filtro por categoria, MoneyInput, toggle "Ver inativos", Desativar/Reativar com tratamento de 409

**NÃO existem ainda (frente B):**
❌ Tela de venda do PDV (catálogo grid + comanda)
❌ Modal de abertura/fechamento de caixa
❌ Fluxo de lançamento de pedido
❌ Fluxo de pagamento (cash, PIX)
❌ Tela de troco
❌ Dashboard de pedidos com timer de atrasos
❌ Gestão de funcionários (user management dentro do tenant)

---

## Decisões de produto tomadas

### Identidade e roles
- Roles fixas (ADMIN/MANAGER/CASHIER), sem matriz customizada por usuário
- SUPER_ADMIN apenas no admin (3001), bloqueado no PDV
- User management dentro do tenant (ADMIN gerencia equipe) — **a implementar (sprint 4f)**

### Soft delete
- Tenants: via `active=false`
- Categorias: via `deletedAt` timestamp
- Produtos: via `deletedAt` timestamp
- Tenant default e master tenant não podem ser desativados
- **UX no PDV**: botões "Desativar" e "Reativar" (não "Excluir") — semântica honesta com a operação (registro continua no banco, recuperável via toggle "Ver inativos"). ConfirmDialog sem `danger: true` para essas ações.

### Catálogo
- Produto pertence sempre a uma categoria (FK NOT NULL)
- Categoria default "Diversos" criada automaticamente para todo tenant
- "Diversos" é editável (nome, ícone) mas não desativável (proteção tripla: backend recusa + frontend desabilita botão + check via `isDefault` no objeto)
- Categoria com produtos vinculados não pode ser desativada
- Sem variações (cada tamanho = produto separado)
- 12 ícones lucide-react: package, utensils, coffee, beer, pizza, cake, salad, ice-cream, snack, sandwich, fish, milk
- **Constraint de nome única é parcial** (`WHERE deleted_at IS NULL`) em categorias e produtos — permite múltiplos inativos com mesmo nome, apenas um ativo. Backend retorna 409 amigável em conflito.
- **Ordem de categorias**: campo numérico "Ordem de exibição" no drawer (mapeia `position`), listagem por `position ASC, name ASC`. Drag-and-drop fica para sprint futura quando justificar.

### Preço (convenções de unidade)
Documentado para evitar repetição do bug "12.5 → R$ 0,13" da Sprint 4a.2:

- **API** (`Product.price`): `BigDecimal` em reais decimais (ex: `12.50`)
- **MoneyInput** estado interno: `number` em centavos (ex: `1250`)
- **Conversões** acontecem apenas nas fronteiras, via helpers em `src/lib/price.ts`:
  - **Send** (form → API): `centsToApiString(cents)` → `(cents / 100).toFixed(2)` (toFixed é obrigatório para evitar drift em valores como R$ 11,99 = `1199/100 = 11.989999...` em JS puro)
  - **Load** (API → form): `apiPriceToCents(apiPrice)` → `Math.round(apiPrice * 100)`
  - **Display** (API → UI): `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(apiPrice)` direto, **sem dividir por 100**
- Nomes explícitos das funções matam confusão de unidade

### Stock (`stockQuantity`/`stockMinLevel`)
- Existem na entidade `Product` e no `ProductRequest` como opcionais
- Backend default 0 se não enviado
- **Fora da tela de catálogo** — pertencem ao módulo INVENTORY (paid)
- Tela específica de estoque será criada quando a sprint de INVENTORY chegar
- Endpoints `/products/low-stock` já anotados com `@RequiresModule("INVENTORY")`

### Distribuição de credenciais (decidida, não implementada)
- Modelo B: convite por link com token, 30 dias de expiração + use-once
- Sem SMTP no MVP — link enviado manualmente por canal externo
- Implementação adiada até aparecer 2º tenant real

### Polimento já consolidado
- Cor de inputs do PDV — corrigido
- Hydration warning — corrigido com `suppressHydrationWarning`
- `addItem` 200 → 201 — corrigido
- `ip_address` em audit_log — corrigido (captura de IP via `HttpServletRequest`)

---

## Dívidas técnicas registradas

1. **`stockQuantity`/`stockMinLevel`** existem na entidade Product mas só serão expostos no frontend na sprint de INVENTORY — endpoints relacionados (`/products/low-stock`) já anotados com `@RequiresModule("INVENTORY")`
2. **Integration tests não rodam em `mvn test` padrão** — exigem flag `-Dexclude.integration.tests=""`. Importante registrar para futuros agentes e pipelines de CI
3. **Tailwind v4 com `@config` legado** — recomendado migrar para `@theme` no CSS principal em sprint futura
4. **PDV: chaves de localStorage** documentadas (`dipdv_token`, `dipdv_user`) — diferentes do admin, intencional
5. **Sem Jest configurado no frontend PDV** — validação atual é `npm run build` + manual browser. Introduzir test runner seria scope creep até aparecer caso que justifique (lógica complexa fora de utility puras)
6. **Drag-and-drop para ordem de categorias** — quando a operação real demonstrar que campo numérico não é suficiente, evoluir a UX

**Resolvida nesta sprint:**
- ~~`categories.position` não exposta~~ → exposta via DTO, persistida no service, listagem ordenada por position. (V12 + frontend 4a.2)

---

## Roadmap reformulado

### Frente A — Catálogo (CONCLUÍDA ✅)
- ✅ **4a.1** — Backend de catálogo (mergeada)
- ✅ **4a.2** — Frontend de gestão de catálogo (mergeada): infra do PDV + telas de categorias e produtos

### Frente B — PDV operacional (próxima)
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

## Lições aprendidas (acumuladas)

- **Agentes simulam validação quando não conseguem rodar o app.** Sempre forçar instrumentação real (logs no browser, fetchs no console) em bugs de runtime.
- **Listar "Resultados Observados" sem ter rodado** é comportamento problemático recorrente. Pedir output verbatim de comandos.
- **Mudanças não commitadas se perdem entre sessões.** Agente que reporta "implementado" sem commit está reportando trabalho temporário. Aconteceu 3 vezes na Sprint 4a.2.
- **Confiar mas verificar.** Checkpoints antigos podem mentir; auditoria periódica via `git log --all --diff-filter=A` revela a verdade.
- **`mvn test` puro exclui integration tests** — sempre rodar com `-Dexclude.integration.tests=""` no DiPDV.

**Novas (Sprint 4a.2):**

- **Force output literal no relatório do agente.** Diretriz dura: ao terminar, agente deve colar últimas 3-15 linhas literais de `mvn test`/`npm run build` + output literal de `git status`. Sem isso, declarações de "verde" e "tudo passou" não são confiáveis. Esta diretriz só foi adotada após 3 episódios de simulação na mesma sprint.
- **JSONPath filter `[?()]` em MockMvc retorna array,** mesmo casando um único registro. Matchers de "empty" rejeitam `[null]`. Usar `contains(nullValue())` ou estratégia alternativa (verificar pelo `name` na chamada não-filtrada de soft-deleted).
- **Spring `Page<T>` envelope** — frontend deve extrair `.content`. Criar tipo `Page<T>` reutilizável em `src/lib/types.ts` evita re-inventar a cada endpoint paginado.
- **Conversões de unidade pertencem a utility nomeada** (`src/lib/price.ts`), nunca inline em componente. Nomes explícitos (`centsToBRL`, `apiPriceToCents`) matam confusão de unidade. O bug `12.5 → R$ 0,13` foi causado por divisão por 100 duplicada: backend já entregava decimal, frontend dividiu novamente.
- **Unique constraints com soft delete devem ser partial indexes** (`WHERE deleted_at IS NULL`). Constraint absoluta + soft delete = 500 garantido ao reativar ou recriar com mesmo nome. Migrations V12 e V13 corrigem esse padrão para categorias e produtos.

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
- Adicionar lição aprendida nova quando padrão de bug se repete (não esperar terceiro episódio)
