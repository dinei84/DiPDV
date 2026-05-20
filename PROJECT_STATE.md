# PROJECT_STATE.md — DiPDV

> Estado atualizado em 20/05/2026 após encerramento da Sprint 6.1
> (Gestão de Usuários e Ajustes de Segurança/Navegação). 

---

## Stack consolidada

### Backend
- Java 21 + Spring Boot 3.3.x
- PostgreSQL 16 com Row-Level Security (RLS) total
- Flyway migrations: V1 → V16
- Spring Security + JWT
- AOP para feature gating (`@RequiresModule`)
- Testcontainers para testes IT
- **Total atual de testes: 209 verdes** (`mvn test -Dexclude.integration.tests=""`)
- Swagger/OpenAPI 3 em `/swagger-ui/index.html`

### Frontend PDV (`frontend/`)
- Next.js 16.2.0 + React 19.2.4 + TypeScript 5
- Tailwind v4 (raw, sem shadcn)
- lucide-react para ícones
- localStorage para JWT (chaves: `dipdv_token`, `dipdv_user`)
- Porta 3000
- PDV: Abertura/Fechamento de Caixa, Comandas, Itens, Pagamentos
- Gestão: Categorias, Produtos, Modificadores, Equipe (Roles)
- Role Gating: ADMIN (Equipe), MANAGER (Catálogo/Relatórios), CASHIER (Operacional)

### Frontend Admin (`admin/`)
- Mesma stack do PDV
- localStorage com chaves separadas (`dipdv_admin_token`, `dipdv_admin_user`)
- Porta 3001
- Apenas SUPER_ADMIN tem acesso (Login via cookie seguro)

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
- **SUPER_ADMIN**: cross-tenant. Gerencia tenants e módulos no admin/3001.
- **ADMIN**: dono do tenant. Acesso total. Gerencia equipe (usuários) e catálogo.
- **MANAGER**: supervisor. Gerencia catálogo e vê relatórios. Abre/fecha caixa.
- **CASHIER**: operador. Lança pedidos, recebe pagamentos, opera caixa.

Roles fixas, sem matriz customizada.

---

## Estado atual por componente

### Backend — completo e estável

✅ **Auth** — login global por email (desambiguado), JWT, role check via `@PreAuthorize`
✅ **Multi-tenancy** — RLS em todas as tabelas tenant, bypass seguro para login e super-admin
✅ **Feature gating** — `@RequiresModule` + ModuleGatingAspect, endpoints `/api/v1/admin/modules/*` e `/api/v1/me/modules`
✅ **Tenants** — CRUD completo `/api/v1/admin/tenants`, soft delete via `active=false`, proteção de tenant default e master
✅ **Catalog** — CRUD completo de produtos, categorias e modificadores. Soft delete via `deletedAt`. Partial unique indexes corrigidos.
✅ **Cash register** — CRUD completo, consumido pelo frontend
✅ **Orders** — Gestão de comandas e itens, consumido pelo frontend
✅ **Payments** — Registros de pagamento (Cash, PIX, Card), integração fiscal mock (NFC-e)
✅ **Reports** — Endpoints financeiros e dashboard, consumidos pelo frontend
✅ **User Management** — CRUD de funcionários (MANAGER/CASHIER) dentro do tenant

**Total de testes:** 209 verdes (`mvn test -Dexclude.integration.tests=""`)

### Admin (porta 3001) — completo

✅ Login + AdminGuard (apenas SUPER_ADMIN)
✅ Sistema de toast (Context + Provider, bottom-right)
✅ Lista de tenants
✅ Criação de tenant (com auto-criação de categoria "Diversos")
✅ Edição de tenant (nome, slug, módulos, ativo/inativo)
✅ Toggle de módulos com feedback otimista
✅ Toggle de ativação/desativação com modal de confirmação
✅ Proteção de tenant default e master tenant contra desativação

### Frontend PDV (porta 3000) — completo (v1)

✅ Login desambiguado (apenas email e senha)
✅ AuthGuard + Role-based redirects
✅ Navigation: Logo inteligente (Home operacional), Menu Gestão (ADMIN/MANAGER), Menu Relatórios (ADMIN/MANAGER)
✅ `/manage/users` — Gestão de equipe (CRUD + Desativar/Reativar)
✅ `/manage/categories` — Gestão de categorias
✅ `/manage/products` — Gestão de catálogo
✅ `/pdv` — Operacional (Caixa, Comandas, Pagamentos)
✅ `/reports` — Relatórios financeiros

---

## Decisões de produto tomadas

### Identidade e roles
- **Login Global**: O email deve ser único entre usuários ativos na plataforma. Isso permite login sem informar o tenantId.
- **Role Gating**: 
  - `ADMIN`: Apenas ADMIN gerencia a equipe (`/manage/users`).
  - `MANAGER`: Pode gerenciar produtos/categorias e ver relatórios, mas não gerencia equipe.
  - `CASHIER`: Acesso apenas ao operacional (`/pdv` e `/orders`).

### RLS & Login
- A tabela `users` possui uma política de RLS estendida que permite SELECT global se a flag `app.bypass_rls_for_login` estiver ativa. Isso resolve o problema de encontrar o usuário antes de saber o seu `tenant_id`.

---

## Lições aprendidas (acumuladas)

- **RLS & Login Cross-tenant**: Em sistemas multi-tenant com RLS, o login exige um bypass temporário e seguro para localizar o usuário pelo email em toda a plataforma.
- **Short-circuit em SQL**: PostgreSQL nem sempre faz short-circuit em `OR` se envolver erros de cast. Usar `CASE` ou `NULLIF` para garantir robustez em políticas de RLS.
- **Controlled Components**: Sempre garantir que campos de formulário (especialmente email e senha) sejam resetados explicitamente no estado ao abrir drawers de criação para evitar vazamento de dados ou autofill incorreto.

---

## Próximos Passos (Sprint 7)
1.  **Deploy Render**: Configurar build e deploy automático.
2.  **PWA Mínimo**: Manifest e Service Worker básico para modo offline parcial.
3.  **Bootstrap em Produção**: Seed de produção e validação final.
4.  **Merge feature/user-management -> develop**.

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
