# DiPDV — Contexto para o Tech Lead (Claude Chat)

> Colar no início de uma nova conversa para restaurar o contexto.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.3.x |
| Frontend PDV | Next.js 14 + Tailwind (localStorage JWT) |
| Frontend Admin | Next.js 14 + Tailwind (cookie HttpOnly) |
| Banco | PostgreSQL 16 + RLS + Flyway V1..V8 |
| Testes | JUnit 5 + Mockito + Testcontainers |
| Deploy | Railway (pendente) |
| CI | GitHub Actions |

## Segurança

- Multi-tenancy: Shared Schema + RLS (`app.current_tenant`)
- Kill Switch SUPER_ADMIN: `app.is_super_admin=true` AND `current_tenant=ffffffff...`
- JWT em header (PDV) ou cookie HttpOnly `dipdv_admin_token` (Admin)
- Roles: `CASHIER < MANAGER < ADMIN < SUPER_ADMIN`

## Módulos backend

`auth`, `catalog`, `order`, `payment`, `cashregister`, `inventory`,
`nfce`, `report`, `admin` + shared: `audit`, `security`, `tenant`, `exception`

## Padrões do projeto

- Erros: `BusinessException(message, HttpStatus)` → `GlobalExceptionHandler` → `ApiError`
- Auditoria: `@Auditable(action, entity)` → `AuditAspect` → `audit_log`
- Tenant: `TenantContext.getRequired()` nos Services
- DTOs: Java `record` — sufixo `Request`/`Response`
- Soft delete em `Product` e `User` via `deletedAt`
- Optimistic Locking em `Order` via `@Version`

## Estado atual dos testes

```
Unitários:  59 testes (Services com Mockito)
RLS:        10 testes (Testcontainers + PostgreSQL real)
Controllers: ~30 testes (T2 — em andamento)
E2E:        0 (T3 — pendente)
```

## Branches ativas

```
feature/test-rls-integration    ← plano de testes ativo
feature/US-SA01-super-admin-infra ← Sprint 4 backend
feature/US-SA03-admin-frontend   ← Sprint 4 frontend
```

## Próximas entregas

1. T2 — Controller tests (em execução)
2. T3 — Fluxo E2E de venda (próximo)
3. T4 — Admin cross-tenant tests
4. Deploy Railway

## Regras de comunicação

- Não repetir código já implementado — referenciar por nome de classe
- Relatórios do executor: apenas arquivos alterados + contagem de testes + desvios
- Se esta conversa ficar pesada: emitir checkpoint e pedir novo chat
