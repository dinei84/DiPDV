# Prompt — Backend: Feature Gating por Módulos

Leia `AGENTS.md` antes de começar.
Branch: `feature/module-gating` (a partir de `develop`).

---

## Objetivo

Implementar sistema de módulos liberáveis por tenant. SUPER_ADMIN controla
o que cada tenant tem ativo. Endpoints já existentes permanecem intocados
em comportamento — apenas ganham anotação `@RequiresModule` onde aplicável.

Frontend (admin/ e PDV) **não** entra neste prompt.

---

## Migrations

`V10__create_modules.sql`:

- Tabela `modules` (catálogo global, sem tenant_id):
  `code TEXT PK`, `name TEXT NOT NULL`, `description TEXT`,
  `tier TEXT NOT NULL CHECK (tier IN ('BASE', 'PAID'))`,
  `created_at TIMESTAMPTZ DEFAULT NOW()`.

- Tabela `tenant_modules`:
  `tenant_id UUID`, `module_code TEXT REFERENCES modules(code)`,
  `enabled BOOLEAN NOT NULL DEFAULT TRUE`,
  `enabled_at TIMESTAMPTZ DEFAULT NOW()`, `enabled_by UUID`,
  PK composta `(tenant_id, module_code)`.
  RLS habilitado, mesma política dos outros tenant tables — exceto que
  SUPER_ADMIN bypass continua valendo (já existe na convenção).

- Seed dos módulos no catálogo:
  - BASE: `PDV_BASIC`, `CATALOG_MANAGEMENT`
  - PAID: `PAYMENT_PIX`, `PAYMENT_CARD`, `REPORTS`, `INVENTORY`,
    `WHATSAPP_ORDERS`, `IFOOD_INTEGRATION`, `LOYALTY`

- Backfill: para todo tenant existente em `tenants`, inserir linhas em
  `tenant_modules` ativando **apenas os módulos BASE**. Tenants já cadastrados
  ficam com tier base liberado por padrão (não quebra os 145 testes).

---

## Backend — código

### `ModuleService`
- `isEnabled(UUID tenantId, String moduleCode): boolean`
  Retorna `true` se módulo é tier BASE OU se existe linha ativa em
  `tenant_modules` para o tenant. SUPER_ADMIN: sempre `true` (cross-tenant).
- `listEnabledModules(UUID tenantId): List<String>`
- `enableModule(UUID tenantId, String code, UUID actorUserId): void`
  (apenas SUPER_ADMIN — controller fará a checagem de role)
- `disableModule(UUID tenantId, String code, UUID actorUserId): void`
  Bloquear desabilitar módulos BASE — lança `IllegalStateException`.

### Anotação + Interceptor

`@RequiresModule("CODE")` em método de controller.
Aspect ou `HandlerInterceptor` resolve `tenantId` do `SecurityContext`,
chama `ModuleService.isEnabled`, retorna **403 Forbidden** com payload
`{ "error": "MODULE_NOT_ENABLED", "module": "CODE" }` se inativo.

Importante: SUPER_ADMIN pula o gating (já é cross-tenant por design).

### Aplicação nos controllers existentes

Aplicar `@RequiresModule` apenas onde o módulo é **PAID**. Endpoints de
tier BASE não recebem anotação — comportamento idêntico ao atual.

Mapeamento mínimo deste prompt (resto vira sprint própria à medida que
funcionalidades nascem):

| Controller / método                                | Módulo exigido |
|----------------------------------------------------|----------------|
| Métodos de relatório existentes (se houver)        | `REPORTS`      |
| (demais endpoints atuais ficam sem anotação)       | —              |

Se hoje não existe controller de relatório, o mapeamento fica vazio neste
prompt — o que é OK. Importante é o mecanismo estar pronto.

### `ModuleAdminController` (cross-tenant, SUPER_ADMIN-only)

Endpoints novos sob `/api/v1/admin/modules`:

- `GET /catalog` → lista de todos os módulos do catálogo
- `GET /tenants/{tenantId}` → módulos ativos para um tenant específico
- `POST /tenants/{tenantId}/enable` body `{ "code": "..." }`
- `POST /tenants/{tenantId}/disable` body `{ "code": "..." }`

Todos protegidos por `@PreAuthorize("hasRole('SUPER_ADMIN')")`.

### `TenantContextController` ou similar

Endpoint para o frontend descobrir o que está ativo no próprio tenant:

- `GET /api/v1/me/modules` → `["PDV_BASIC", "CATALOG_MANAGEMENT", ...]`
  (qualquer usuário autenticado, retorna módulos do próprio tenant)

---

## Testes

Mínimo necessário, padrão Testcontainers já consolidado:

1. **`ModuleServiceTest`** (unit): isEnabled retorna true para BASE,
   true para PAID ativo, false para PAID inativo. SUPER_ADMIN sempre true.
2. **`ModuleGatingIT`** (integration): controller fictício com endpoint
   `@RequiresModule("REPORTS")` — chamada com módulo ativo retorna 200,
   sem módulo retorna 403 com payload correto, SUPER_ADMIN bypassa.
3. **`ModuleAdminControllerIT`**: SUPER_ADMIN consegue enable/disable;
   ADMIN comum recebe 403; tentar disable de módulo BASE retorna 400.
4. **Regressão**: rodar suite completa — os 145 testes atuais devem
   continuar verdes (graças ao backfill ativando BASE).

Meta: ≈10 testes novos, 145 anteriores intactos.

---

## Fora do escopo (não fazer)

- Frontend de qualquer tipo.
- Aplicar `@RequiresModule` em endpoints futuros (PIX, Card, WhatsApp etc.)
  — esses módulos ainda não têm controller. Aplicação acontece quando
  cada feature nascer.
- Histórico/auditoria de enable/disable além do `enabled_at`/`enabled_by`.
- Período de validade (datas de início/fim do contrato) — fica para
  evolução pós-MVP.

---

## Relatório esperado (minimalista)

- Migrations criadas e versão.
- Lista de classes novas + classes alteradas (sem corpo).
- Total de testes: anterior → novo (deve ser 145 → ~155).
- Endpoints novos sob `/api/v1/admin/modules` e `/api/v1/me/modules`.
- Desvios da especificação, se houver.
