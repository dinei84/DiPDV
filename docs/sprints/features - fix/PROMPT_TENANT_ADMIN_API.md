# Prompt — Backend: TenantAdminController

Leia `AGENTS.md` antes de começar.
Branch: `feature/tenant-admin-api` (a partir de `develop`).

---

## Objetivo

Expor CRUD de tenants para o painel SUPER_ADMIN consumir.
Tabela `tenants` já existe (Flyway V1). Repository já existe.
Este prompt só adiciona service + controller + testes.

Frontend não entra aqui.

---

## Endpoints novos

Sob `/api/v1/admin/tenants`, todos com `@PreAuthorize("hasRole('SUPER_ADMIN')")`:

| Método | Path                       | Função                            |
|--------|----------------------------|-----------------------------------|
| GET    | `/`                        | Lista todos os tenants            |
| GET    | `/{tenantId}`              | Busca tenant por id               |
| POST   | `/`                        | Cria tenant novo                  |
| PUT    | `/{tenantId}`              | Atualiza nome / status do tenant  |

Sem DELETE neste prompt — desativação é via campo `active` (soft).
Se o schema atual da tabela `tenants` não tem `active`, criar
migration `V11__add_tenants_active.sql` adicionando `active BOOLEAN
NOT NULL DEFAULT TRUE`.

---

## Comportamento esperado

### Criação
- Body: `{ "name": "Lanchonete X", "slug": "lanchonete-x" }`
  (slug opcional — gerar a partir do name se vier null).
- Backend cria tenant E ativa automaticamente módulos do **tier BASE**
  para ele (`PDV_BASIC`, `CATALOG_MANAGEMENT`).
  Reusar `ModuleService.enableModule` por código BASE.
- Retorna 201 + `TenantResponse`.

### Atualização
- Body parcial: `{ "name": "...", "active": true/false }`.
- Desativar tenant (`active=false`) **não** apaga dados — apenas impede
  login. Login flow deve checar `tenant.active` (ajustar `AuthService`
  se necessário, retornando 403 com mensagem clara).

### Listagem
- Sem paginação neste prompt — banco terá poucos tenants no MVP.
  Ordenação: `created_at DESC`.

---

## DTOs (records)

- `TenantRequest(String name, String slug, Boolean active)` — todos
  opcionais para suportar create e update parcial.
- `TenantResponse(UUID id, String name, String slug, boolean active,
  Instant createdAt, List<String> enabledModules)`.
  `enabledModules` vem de `ModuleService.listEnabledModules`.

---

## Testes (`TenantAdminControllerIT`)

Padrão Testcontainers já consolidado. Mínimo:

1. SUPER_ADMIN lista → 200 com array.
2. ADMIN comum tenta listar → 403.
3. SUPER_ADMIN cria tenant → 201; resposta inclui `enabledModules`
   contendo `PDV_BASIC` e `CATALOG_MANAGEMENT`; banco confirma linhas
   em `tenant_modules`.
4. SUPER_ADMIN atualiza nome → 200; campo persistido.
5. SUPER_ADMIN desativa tenant → 200; ADMIN daquele tenant tenta
   login → 403 ou erro claro.
6. SUPER_ADMIN busca tenant inexistente → 404.

Meta: 6 testes novos, suite anterior intacta (156 → ~162).

---

## Fora do escopo

- DELETE / hard delete.
- Paginação ou filtros na listagem.
- Tela frontend.
- Endpoint para listar usuários do tenant (vira sprint própria
  quando user management entrar em cena).
- Auditoria além de `created_at` / `updated_at`.

---

## Relatório esperado (minimalista)

- Migration nova (se aplicável).
- Classes novas e alteradas (sem corpo).
- Total de testes: 156 → novo total.
- Endpoints novos sob `/api/v1/admin/tenants`.
- Desvios da especificação, se houver.
