# PROJECT_STATE.md — DiPDV

> Estado atualizado em 20/05/2026 após auditoria completa pós-merge da Sprint 6.3.
> Sprint 6.3: dashboard de comandas com indicador de tempo (home unificada para todos os roles).
> Documento reconstruído a partir de inventário literal do código (não de relatórios de agente).

---

## Stack consolidada

### Backend
- Java 21 + Spring Boot 3.3.x
- PostgreSQL 16 com Row-Level Security (RLS) em todas as tabelas tenant
- Flyway migrations: **V1 → V16** (16 migrations aplicadas, schema estável)
- Spring Security + JWT
- AOP para feature gating (`@RequiresModule`)
- Testcontainers para testes IT
- **Total atual de testes: 209 verdes** (`mvn test -Dexclude.integration.tests=""`)
- 14 controllers, 17 services, 19 repositories, 27 classes de domínio/entidade

### Frontend PDV (`frontend/`)
- Next.js 16.2.0 + React 19.2.4 + TypeScript 5
- Tailwind v4 (raw, sem shadcn)
- lucide-react para ícones
- localStorage para JWT (chaves: `dipdv_token`, `dipdv_user`)
- Porta 3000
- Estrutura: `src/app/(auth)/login` + `src/app/(pdv)/...` (rotas operacionais protegidas por AuthGuard no layout)
- Sem PWA configurado (sem manifest, sem service worker, sem ícones próprios — só os 5 SVGs default do Next)

### Frontend Admin (`admin/`)
- Mesma stack do PDV (Next 16.2.0 + React 19.2.4)
- localStorage com chaves separadas (`dipdv_admin_token`, `dipdv_admin_user`)
- Porta 3001
- Apenas SUPER_ADMIN tem acesso (`AdminGuard`)
- Sem PWA, sem `public/` (nem o diretório existe)
- Tem Sidebar + Header próprios, sistema de Toast próprio (separado do PDV)

### Ambiente local
- Docker container `dipdv-postgres` (postgres:16), porta **5433** (host) → 5432 (container)
- Container pgAdmin opcional `dipdv-pgadmin` em `http://localhost:5050`
- Database `dipdv_dev`, user `dipdv_app`, password `dipdv_local_2025`
- Scripts de start na raiz do repo:
  - `./start-backend-dev.sh` — exporta DB_URL/USERNAME/PASSWORD e roda Spring Boot
  - `./start-frontend-dev.sh` — roda Next dev no PDV (porta 3000)
  - `./start-admin-dev.sh` — roda Next dev no Admin (porta 3001)
- Backend lê config de env vars com fallback em `application-dev.yml`. **NUNCA** rodar `mvn spring-boot:run` direto sem exportar as env vars (default do yml aponta pra porta 5432, que não é a que está em uso).

---

## Modelo de negócio

SaaS B2B multi-tenant com **feature gating por tenant**.

### Tier base (sempre liberado)
- `PDV_BASIC`
- `CATALOG_MANAGEMENT`

### Tier paid (liberado pelo SUPER_ADMIN via admin/3001)
- `PAYMENT_PIX`
- `PAYMENT_CARD`
- `REPORTS`
- `INVENTORY`
- `WHATSAPP_ORDERS`
- `IFOOD_INTEGRATION`
- `LOYALTY`

Tenant nasce **só com tier base** + categoria default "Diversos". SUPER_ADMIN libera o restante manualmente.

⚠️ Importante: tenant default de dev (`Lanchonete Dev`, `00000000-0000-0000-0000-000000000001`) tem `modules: {NULL}` no banco — ou seja, nem o tier base aparece em `tenant_modules` explicitamente. As features tier-base funcionam mesmo assim (são checadas com fallback no código).

### Roles e matriz de permissões

| Recurso                                  | ADMIN | MANAGER | CASHIER |
|------------------------------------------|-------|---------|---------|
| Home `/` (dashboard de comandas abertas) | ✅    | ✅      | ✅      |
| Catálogo — categorias + produtos         | ✅    | ✅      | ❌      |
| Equipe (`/manage/users`)                 | ✅    | ❌      | ❌      |
| Relatórios (`/reports`)                  | ✅    | ✅      | ❌      |
| Caixa — visualizar status                | ✅    | ✅      | ✅      |
| Caixa — abrir / fechar / sangria         | ✅    | ✅      | ❌      |
| Comandas — listar + lançar item          | ✅    | ✅      | ✅      |
| Comandas — cancelar pedido               | ✅    | ✅      | ❌      |
| Pagamento (cash, PIX)                    | ✅    | ✅      | ✅      |
| Gestão de tenants/módulos (admin/3001)   | SUPER_ADMIN exclusivo                |

Enforcement em três camadas:
- **Backend**: `@PreAuthorize("hasRole(...)")` em todos os controllers de mutação
- **Client-side route**: `<RoleGuard>` em cada rota gated
- **Client-side botão**: check via leitura de `getAuth()` para esconder/desabilitar ações

Roles fixas, sem matriz customizada por usuário.

---

## Estado atual por componente

### Backend — completo e estável

**Controllers (14):** AdminController, AuthController, CashRegisterController, CategoryController, ModifierController, ModuleAdminController, NfceController, OrderController, PaymentController, ProductController, ReportController, TenantAdminController, TenantContextController, UserManagementController.

**Services (17):** AdminMetricsService, AdminTenantService, AuthService, CashRegisterService, CatalogService, JwtService, MockNfceService, ModifierService, ModuleService, NfceService, OrderService, PaymentService, PdfReportService, ReportService, TenantAdminService, TenantContextService, UserManagementService.

**Repositories (19):** AdminRepository, AuditLogRepository, CashMovementRepository, CashRegisterRepository, CategoryRepository, ModifierGroupRepository, ModifierOptionRepository, ModuleRepository, NfceDocumentRepository, OrderItemRepository, OrderRepository, PaymentRepository, ProductModifierGroupRepository, ProductRepository, ReportRepository, StockMovementRepository, TenantModuleRepository, TenantRepository, UserRepository.

**Status por módulo:**
- ✅ **Auth** — login global por email (sem tenant_id no payload), JWT, role check via `@PreAuthorize`. AuthService com bypass RLS controlado para login.
- ✅ **Multi-tenancy** — RLS em todas as tabelas tenant. Bypass específico via `app.bypass_rls_for_login` para auth, e via `app.is_super_admin` + master tenant para SUPER_ADMIN.
- ✅ **Feature gating** — `@RequiresModule` + ModuleGatingAspect, endpoints `/api/v1/admin/modules/*` e `/api/v1/me/modules`.
- ✅ **Tenants** — CRUD completo, soft delete via `active=false`, proteção de tenant default e master, criação do primeiro ADMIN via dialog no admin/3001.
- ✅ **Catalog** — produtos, categorias e modificadores. Soft delete via `deletedAt`. Partial unique indexes (`WHERE deleted_at IS NULL`). Auto-criação de "Diversos". Entidades: Category, Product, ModifierGroup, ModifierOption, ProductModifierGroup, ProductModifierGroupId. **Modificadores têm backend pronto mas frontend ainda não tem tela dedicada para gerenciá-los.**
- ✅ **Cash register** — CRUD completo, consumido pelo frontend. Entidades: CashRegister, CashRegisterStatus, CashMovement, CashMovementType. Componentes frontend: CashRegisterDrawer, CashRegisterIndicator, CloseCashRegisterDialog.
- ✅ **Orders** — CRUD de pedidos + itens, comanda com identifier (V14), validação de conflito de identifier por tenant. Entidades: Order, OrderItem, OrderItemModifier, OrderStatus. Backend retorna `OrderSummary` (lista) e `Order` (detalhe).
- ✅ **Payments** — múltiplos meios, idempotência, auditoria (Sprint 5.3). Entidades: Payment, PaymentMethod, PaymentStatus. Vinculados a `cash_register_id` desde V7 (FK obrigatória).
- ✅ **NFC-e** — mock implementation, endpoints prontos. Entidades: NfceDocument, NfceStatus. Tier paid futuro.
- ✅ **Reports** — endpoints `/api/v1/reports/*` (summary, payment-methods, top-products, engagement, pdf). Consumidos pelo frontend.
- ✅ **User Management** — CRUD de funcionários dentro do tenant, ADMIN only. Entidade User com role.
- ⚠️ **Stock movements** — entidade StockMovement existe, repositório existe. **Não há controller ainda** (endpoint REST não exposto). Reservado para módulo INVENTORY futuro.
- ✅ **Audit log** — entidade existe, populada parcialmente via AuditLogRepository.

### Admin (porta 3001) — completo

- ✅ Login + AdminGuard (apenas SUPER_ADMIN)
- ✅ Sistema de toast próprio (Context + Provider)
- ✅ Sidebar + Header próprios
- ✅ Lista de tenants
- ✅ Criação de tenant + dialog de criação do primeiro ADMIN (`FirstAdminDialog`)
- ✅ Edição de tenant (`/tenants/[id]`) — nome, slug, módulos, ativo/inativo
- ✅ Toggle de módulos com feedback otimista
- ✅ Toggle de ativação/desativação com modal de confirmação (`ConfirmModal`)
- ✅ Proteção de tenant default e master tenant contra desativação
- ✅ Página `/dashboard` para SUPER_ADMIN (métricas cross-tenant)

### Frontend PDV (porta 3000) — operacional completo (v1)

**Infraestrutura compartilhada (`src/lib/`):**
- `api.ts` + `api-error.ts` + `api-url.ts` — wrapper de fetch com interceptação global de 401/403 → toast automático, ApiError tipado
- `auth.ts` — `saveAuth`, `getAuth`, `clearAuth`, `isAuthenticated`
- `price.ts` — helpers `centsToBRL`, `apiPriceToCents`, `centsToApiString`
- `types.ts` — DTOs (Order, OrderSummary, OrderItem, Product, Category, Page<T>, etc)
- `confirm/` — ConfirmDialog Promise-based (3 arquivos)
- `toast/` — sistema completo (bus + provider + types + Toaster, 5 arquivos)
- `hooks/useModules.tsx` — verifica feature gating
- `cash-register/CashRegisterContext.tsx` — estado global do caixa
- `orders/OrdersContext.tsx` — CRUD de comanda/item via API, gerencia `currentOrderId` e `openOrders`
- `orders/orderTimeThresholds.ts` — constantes de threshold + função `classifyOrderTime` (status fresh/warning/critical)
- `orders/useOrdersPolling.ts` — polling de comandas abertas (default 60s)
- `orders/useOrderTimeRefresh.ts` — re-render periódico para recalcular tempo (default 30s)

**Componentes (`src/components/`):**
- `AuthGuard.tsx` — proteção de rotas
- `RoleGuard.tsx` — proteção por role
- `ModuleGate.tsx` + `ModuleNotAvailable.tsx` — feature gating
- `MoneyInput.tsx` — input em centavos, display BRL
- `CashRegister/` — Drawer, Indicator, CloseCashRegisterDialog
- `Orders/` — CatalogGrid, CurrentOrderPanel, NewOrderDialog, OpenOrdersDrawer, OpenOrdersIndicator, OrderCard, OrdersDashboard, PaymentDialog, CancelOrderDialog
- `dashboard/` — DashboardWidget, PaymentChart (KPIs do dia, agora consumidos por `/reports`)
- `reports/` — ReportFilters, TopProductsTable

**Rotas:**
- `/login` — autenticação por email/senha (sem tenant_id)
- `/` — home unificada: renderiza `<OrdersDashboard />` para qualquer role autenticado (Sprint 6.3 addendum)
- `/pdv` — tela operacional: catálogo à esquerda + comanda à direita (split-screen lg+). Mobile mostra placeholder `TODO`
- `/manage/categories` — gestão de categorias (ADMIN/MANAGER)
- `/manage/products` — gestão de produtos (ADMIN/MANAGER)
- `/manage/users` — gestão de equipe (ADMIN only)
- `/reports` — DashboardWidget (KPIs do dia) + filtros + top products + PDF download. Atrás de ModuleGate REPORTS

**NÃO existem ainda:**
- ❌ Gestão de modificadores no frontend (backend pronto)
- ❌ Tela operacional do PDV em viewport mobile (`TODO: Implementar drawer de comanda para mobile` em `pdv/page.tsx`)
- ❌ Persistência de `currentOrderId` em refresh (estado em React state apenas)
- ❌ PWA (manifest + service worker + ícones próprios)
- ❌ Tela de inventário/estoque (módulo INVENTORY)
- ❌ Configuração de thresholds de tempo por tenant (hoje fixo no código, comentado para migração futura)

---

## Decisões de produto tomadas

### Identidade e login
- **Login global por email**: email é único entre usuários ativos da plataforma (partial unique index `WHERE active=true`, V16). Login pede apenas email + senha; backend resolve tenant a partir do usuário encontrado.
- **RLS bypass controlado para login**: tabela `users` tem policy que permite SELECT global quando flag de sessão `app.bypass_rls_for_login` está `true`. A flag é ativada apenas dentro do `AuthService.login()`, em `try` com `finally` que limpa a flag mesmo em exceção. O `WITH CHECK` da policy **não inclui** o bypass — escrita cross-tenant continua bloqueada.
- **Roles fixas** (ADMIN/MANAGER/CASHIER), sem matriz customizada por usuário.
- **SUPER_ADMIN** apenas no admin (3001), bloqueado no PDV.
- **User management dentro do tenant**: ADMIN gerencia equipe via `/manage/users`. Primeiro ADMIN de um tenant é criado pelo SUPER_ADMIN no admin/3001, via dialog `FirstAdminDialog`.

### Home unificada (Sprint 6.3)
- **Home `/` é única para todos os roles**: renderiza `<OrdersDashboard />` (cards de comandas abertas com classificação visual por tempo).
- KPIs do dia (DashboardWidget) ficam consolidados em `/reports`, não na home. Isso elimina dependência da home no módulo REPORTS — qualquer tenant funciona sem tela branca, independente de quais módulos paid estão habilitados.
- Tempo das comandas: thresholds fixos (verde <10min, amarelo 10-25min, vermelho >25min), documentados em `orderTimeThresholds.ts` com plano de migração para configurável por tenant.

### Navegação entre comandas (Sprint 6.2)
- Selecionar comanda do `OpenOrdersDrawer` em qualquer tela leva para `/pdv` com a comanda selecionada.
- Criar comanda nova fora de `/pdv` também leva para `/pdv` (Estratégia B: `router.push` dentro do `createOrder` do Context — única fonte de verdade).
- Logo "DiPDV" no header leva ADMIN/MANAGER para `/` (home unificada) e CASHIER também para `/` (mesma tela, comportamento consistente).

### Soft delete
- Tenants: via `active=false`
- Categorias, Produtos, Usuários: via `deletedAt` timestamp
- Tenant default e master tenant não podem ser desativados
- **UX no PDV**: botões "Desativar" e "Reativar" (não "Excluir") — semântica honesta com a operação. ConfirmDialog sem `danger: true` para essas ações.

### Catálogo
- Produto pertence sempre a uma categoria (FK NOT NULL)
- Categoria default "Diversos" criada automaticamente para todo tenant
- "Diversos" é editável (nome, ícone) mas não desativável (proteção tripla: backend recusa + frontend desabilita botão + check via `isDefault` no objeto)
- Categoria com produtos vinculados não pode ser desativada
- Sem variações (cada tamanho = produto separado)
- 12 ícones lucide-react: package, utensils, coffee, beer, pizza, cake, salad, ice-cream, snack, sandwich, fish, milk
- **Constraint de nome única é parcial** (`WHERE deleted_at IS NULL`) em categorias e produtos — permite múltiplos inativos com mesmo nome, apenas um ativo. Backend retorna 409 amigável em conflito.
- **Ordem de categorias**: campo numérico "Ordem de exibição" no drawer (mapeia `position`), listagem por `position ASC, name ASC`. Drag-and-drop fica para sprint futura.
- **Modificadores**: backend e entidades prontas (`ModifierGroup`, `ModifierOption`, `ProductModifierGroup`), mas frontend ainda não tem tela de gestão. Sprint futura.

### Comandas
- Cada comanda tem `identifier` opcional (ex: "Mesa 5", "Balcão", "João") — V14
- **Partial unique index** garante que não há duas comandas OPEN com mesmo identifier no mesmo tenant simultaneamente. Permite reuso depois que comanda fecha/cancela
- Status: OPEN, CLOSED, CANCELLED (verificar enum exato em `OrderStatus.java`)
- Tempo é contado a partir de `createdAt`
- Polling de listagem: 60s (`useOrdersPolling`); refresh de tempo decorrido: 30s (`useOrderTimeRefresh`)
- Sem WebSocket/SSE no MVP

### Preço (convenções de unidade)
Documentado para evitar repetição do bug "12.5 → R$ 0,13" da Sprint 4a.2:

- **API** (`Product.price`): `BigDecimal` em reais decimais (ex: `12.50`)
- **MoneyInput** estado interno: `number` em centavos (ex: `1250`)
- **Conversões** acontecem apenas nas fronteiras, via helpers em `src/lib/price.ts`:
  - **Send** (form → API): `centsToApiString(cents)` → `(cents / 100).toFixed(2)` (toFixed obrigatório para evitar drift)
  - **Load** (API → form): `apiPriceToCents(apiPrice)` → `Math.round(apiPrice * 100)`
  - **Display** (API → UI): `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(apiPrice)` direto, **sem dividir por 100**
- Nomes explícitos das funções matam confusão de unidade.

### Stock (`stockQuantity`/`stockMinLevel`)
- Existem na entidade `Product` e no `ProductRequest` como opcionais
- Backend default 0 se não enviado
- **Fora da tela de catálogo** — pertencem ao módulo INVENTORY (paid)
- Endpoint `/products/low-stock` anotado com `@RequiresModule("INVENTORY")`
- Entidade `StockMovement` existe mas sem controller (não exposto via REST ainda)

### Distribuição de credenciais (decidida, não implementada)
- Modelo B: convite por link com token, 30 dias de expiração + use-once
- Sem SMTP no MVP — link enviado manualmente por canal externo
- Implementação adiada até aparecer 2º tenant real

### Polimento já consolidado
- Cor de inputs do PDV — corrigido (Sprint 4a.2)
- Hydration warning — corrigido com `suppressHydrationWarning` (Sprint 4a.2)
- `ip_address` em audit_log — corrigido (Sprint 4a.2)
- Logo no header com home unificada (Sprint 6.3) — CASHIER e demais roles vão para `/`
- Botão "PDV" redundante removido do menu (Sprint 6.1)
- Navegação de comanda fora de `/pdv` (Sprint 6.2)
- DashboardWidget consolidado em `/reports` (Sprint 6.3 addendum)

---

## Dívidas técnicas registradas

1. **Modificadores no frontend** — backend completo, sem tela. Sprint futura quando justificar (provavelmente junto com onboarding de cliente de pizzaria/lanchonete que precise muito disso).
2. **PDV operacional em viewport mobile** — `TODO: Implementar drawer de comanda para mobile` em `pdv/page.tsx`. Crítico para tablet retrato e celular. Vai em sprint dedicada junto com PWA.
3. **PWA** — sem manifest, sem service worker, sem ícones próprios. Sprint dedicada (junto com responsividade mobile).
4. **Persistência de `currentOrderId` em refresh** — hoje em React state. F5 perde a comanda selecionada. Polimento, baixa prioridade.
5. **`stockQuantity`/`stockMinLevel`** existem na entidade Product mas só serão expostos no frontend na sprint de INVENTORY.
6. **Stock movements sem controller REST** — entidade e repository existem, endpoint não exposto. Para módulo INVENTORY.
7. **Integration tests não rodam em `mvn test` padrão** — exigem flag `-Dexclude.integration.tests=""`. Documentar para CI pipelines.
8. **Tailwind v4 com `@config` legado** — recomendado migrar para `@theme` em sprint futura.
9. **PDV: chaves de localStorage** documentadas (`dipdv_token`, `dipdv_user`) — diferentes do admin (`dipdv_admin_token`, `dipdv_admin_user`), intencional.
10. **Sem Jest configurado no frontend PDV nem Admin** — validação atual é `npm run build` + smoke test browser manual. Introduzir test runner seria scope creep até aparecer caso que justifique.
11. **Drag-and-drop para ordem de categorias** — quando a operação real demonstrar que campo numérico não é suficiente.
12. **Dependabot: 30 vulnerabilidades** reportadas (15 high, 11 moderate, 4 low). Tratar antes do deploy. Link: `https://github.com/dinei84/DiPDV/security/dependabot`
13. **Hard delete de funcionário** — feedback do cliente. Hoje só soft delete.
14. **Filtro de duplicados na criação de funcionário/admin** — feedback do cliente, baixa prioridade.
15. **Thresholds de tempo de comanda configuráveis por tenant** — hoje fixo. Plano de migração documentado em `orderTimeThresholds.ts`.

**Resolvidas em Sprints recentes:**
- ~~`categories.position` não exposta~~ → Sprint 4a.2
- ~~Constraint de email global vs per-tenant~~ → V16, Sprint 6.1
- ~~CASHIER acessava rotas administrativas via URL direta~~ → RoleGuard + @PreAuthorize completos, Sprint 6.1
- ~~Selecionar/criar comanda fora de `/pdv` não navegava~~ → Sprint 6.2
- ~~Home em branco para ADMIN/MANAGER quando tenant sem REPORTS~~ → Sprint 6.3 addendum (DashboardWidget migrado para `/reports`)

---

## Roadmap

### Frente A — Catálogo (CONCLUÍDA ✅)
- ✅ Sprint 4a.1 — Backend de catálogo
- ✅ Sprint 4a.2 — Frontend de gestão de catálogo (infra + categorias + produtos)

### Frente B — PDV operacional + Equipe (CONCLUÍDA ✅)
- ✅ Sprint 5.1 — Cash register operacional
- ✅ Sprint 5.2 — Venda operacional completa (comanda + lançamento de item)
- ✅ Sprint 5.3 — Pagamento (múltiplos meios, idempotência, auditoria)
- ✅ Sprint 6 — User Management (CRUD de funcionários)
- ✅ Sprint 6.1 — Login global + role gating completo + correções de UX
- ✅ Sprint 6.2 — Hotfix de navegação de comanda
- ✅ Sprint 6.3 — Dashboard de comandas com indicador de tempo + home unificada

### Frente C — Pré-deploy (em andamento)
- **Próxima: Sprint 6.4 (housekeeping de dependências)** — resolver Dependabot (15 high, 11 moderate, 4 low). Não subir produção com vulnerabilidades altas conhecidas.
- **Sprint 7 (PWA + responsividade mobile)** — manifest + service worker + ícones próprios + responsivo em `/pdv` e `/` para tablet retrato e celular.
- Polimento de UX adicional (a definir com base em testes do dono).
- Convite por link (Modelo B) — só quando 2º tenant aparecer.

### Frente D — Deploy (Sprint 8)
- Render com banco de produção
- Variáveis de ambiente, CORS, domínio
- Smoke test em produção
- Documentação operacional

### Frente E — Pós-MVP
- Redesign visual (Claude Design) — diferido até Frentes C e D estarem fechadas
- Módulo INVENTORY (estoque)
- Módulos WHATSAPP_ORDERS, IFOOD_INTEGRATION, LOYALTY
- Telas de gestão de modificadores
- Multi-cliente real (2º+ tenant em produção)

---

## Lições aprendidas (acumuladas)

### Padrões de comportamento de agente (custaram caro de aprender)
- **Agentes simulam validação quando não conseguem rodar o app.** Sempre forçar instrumentação real (logs no browser, fetchs no console) em bugs de runtime.
- **Listar "Resultados Observados" sem ter rodado** é comportamento problemático recorrente. Pedir output verbatim de comandos.
- **Mudanças não commitadas se perdem entre sessões.** Agente que reporta "implementado" sem commit está reportando trabalho temporário.
- **"Validado via simulação", "via curl", "via leitura de código" NÃO é aceite browser.** São validações de outras camadas. Quando o agente não tem Chrome, o último mile fica EXPLICITAMENTE como pendência para humano — não inferir, marcar `[ ]` com nota.
- **Force output literal no relatório do agente.** Últimas 15 linhas de `mvn test`, `npm run build`, `git status`, `git log`. Sem isso, declarações de "verde" não são confiáveis.
- **`mvn test` puro exclui integration tests** — sempre rodar com `-Dexclude.integration.tests=""` no DiPDV.

### Padrões técnicos do projeto (custaram tempo de descobrir)
- **JSONPath filter `[?()]` em MockMvc retorna array,** mesmo casando único registro. Matchers de "empty" rejeitam `[null]`. Usar `contains(nullValue())` ou estratégia alternativa.
- **Spring `Page<T>` envelope** — frontend deve extrair `.content`. Tipo `Page<T>` reutilizável em `src/lib/types.ts`.
- **Conversões de unidade de preço pertencem a utility nomeada** (`src/lib/price.ts`), nunca inline em componente. Nomes explícitos (`centsToBRL`, `apiPriceToCents`) matam confusão.
- **Unique constraints com soft delete devem ser partial indexes** (`WHERE deleted_at IS NULL`). Constraint absoluta + soft delete = 500 garantido ao reativar.
- **RLS & Login cross-tenant**: bypass temporário para localizar usuário pelo email globalmente. Bypass ativado em escopo de transação curta (`try/finally`), policy permite bypass apenas em `USING` (leitura), nunca em `WITH CHECK` (escrita).
- **Controlled components em drawers**: campos de formulário devem ser explicitamente resetados ao abrir drawer em modo "criação". Bug recorrente: `defaultValue={user?.field}` propaga valor estranho quando `user` é parcialmente preenchido.
- **Defesa em profundidade em migrations destrutivas**: migrations que mudam unique constraint global devem ter diagnóstico defensivo (`RAISE EXCEPTION` se dados existentes inviabilizam a mudança). A V16 salvou um banco de dev poluído.

### Padrões arquiteturais (descobertos na Sprint 6.3)
- **ModuleGate silencioso + placeholder removido = regressão invisível.** Quando um placeholder é removido, sempre verificar se o conteúdo "real" depende de feature flags que podem não estar habilitadas em todos os tenants.
- **Sempre validar comportamento em tenant sem nenhum módulo ativo** — é o pior caso e o mais provável em onboarding de cliente novo. Tenant default de dev (`Lanchonete Dev`) deve ser mantido sem módulos paid para servir de canário.
- **Smoke test browser real é insubstituível.** Aceites validados por curl, leitura de código ou IT não capturam: flash de conteúdo antes de redirect, hidratação quebrada, ordem de guards, race conditions com auth, campos pré-preenchidos por engano, regressões por mudança de placeholder.

### Padrões de manutenção do PROJECT_STATE
- **PROJECT_STATE.md é memória cara**. Cada lição custou uma sprint para ser aprendida. Ao atualizar, **nunca reescrever do zero** — adicionar/atualizar preservando histórico. Auditar perda de informação quando ele encolhe entre sprints.
- **Nunca marcar como ✅ o que não está commitado.** Nunca marcar como ✅ feature de backend cujo frontend ainda não consome — registrar como "endpoint pronto, frontend pendente".
- **Manter ambiente local documentado é parte do escopo de toda sprint que mexe em infra.** O nome do container postgres mudou de `global_db` para `dipdv-postgres` e a porta de 5432 para 5433 sem que o documento fosse atualizado — resultado: 30 minutos perdidos investigando "banco vazio" porque o backend conectava em outro lugar.
- **Auditoria literal do código bate suposição de relatório.** Quando documento e código divergem, código é a verdade. Comandos `find`, `grep`, `git log` são autoridade.
- **Mensagens de commit vagas viram dívida de hygiene.** Commits do tipo "subindo arquivos" perdem rastreabilidade. Preferir semantic prefixes (`feat`, `fix`, `docs`, `refactor`, `chore`) e descrição mínima do quê.

---

## Credenciais de desenvolvimento

- Tenant default: `00000000-0000-0000-0000-000000000001` (`Lanchonete Dev`, slug `dev-tenant`)
  - **Sem módulos paid habilitados** intencionalmente (serve de canário para bugs latentes de feature gating)
- Master tenant: `ffffffff-ffff-ffff-ffff-ffffffffffff` (apenas para SUPER_ADMIN)
- `admin@dipdv.dev` / `dipdv@2025` (ADMIN do tenant default)
- `superadmin@dipdv.app` / `SuperAdmin@2025!` (SUPER_ADMIN, login no admin/3001)

---

## Como manter este arquivo

- Atualizar a cada sprint encerrada
- Estado real auditado periodicamente via `git log` + `find` + `grep`
- Nunca registrar como ✅ algo que não foi commitado
- Nunca registrar como ✅ feature de UI que ainda não tem tela consumindo (backend pronto + frontend pendente = "endpoint pronto, frontend pendente", não ✅)
- Adicionar lição aprendida nova quando padrão de bug se repete (não esperar terceiro episódio)
- Quando atualizar, **preservar o que já existe**; o documento cresce, raramente encolhe
- Se encolher entre sprints, **investigar perda de informação** antes de aceitar a versão nova
