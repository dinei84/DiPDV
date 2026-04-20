# DiPDV — Estado do Projeto

> Atualizar após cada sprint/entrega. Substitui relatórios longos.
> Última atualização: 2026-04-20

---

## Testes

| Suite | Testes | Status |
|---|---|---|
| Unitários (Services) | 59 | ✅ |
| RLS (Testcontainers) | 10 | ✅ |
| Controllers (HTTP) | ~30 | 🔄 T2 em execução |
| E2E fluxo de venda | 0 | 🔲 T3 pendente |
| Admin cross-tenant | 0 | 🔲 T4 pendente |

## Migrations Flyway

| Versão | Conteúdo |
|---|---|
| V1 | Schema base + ENUMs |
| V2 | RLS policies |
| V3 | Índices |
| V4 | max_quantity + quantity em modificadores |
| V5 | product_name em order_items |
| V6 | nfce_documents |
| V7 | cash_register_id em payments |
| V8 | SUPER_ADMIN + tenant master + Kill Switch RLS |

## Endpoints ativos

| Módulo | Prefixo | Roles |
|---|---|---|
| Auth | `/api/v1/auth/**` | público |
| Catalog | `/api/v1/categories` `/api/v1/products` | CASHIER+ |
| Modifiers | `/api/v1/modifier-groups` | CASHIER+ / ADMIN |
| Order | `/api/v1/orders` | CASHIER+ |
| Payment | `/api/v1/payments` | CASHIER+ |
| CashRegister | `/api/v1/cash-registers` | CASHIER+ |
| NFC-e | `/api/v1/nfce` | CASHIER+ |
| Reports | `/api/v1/reports` | MANAGER+ |
| Admin | `/api/v1/admin/**` | SUPER_ADMIN |

## Bugs conhecidos / débitos técnicos

- `CategoryControllerSecurityIT` instável sem banco ativo
- `last_activity_at` em tenants: update via `OrderService.closeOrder()` ✅
- `ip_address inet` no `AuditLog`: campo ignorado no insert (débito)
- Admin frontend: smoke tests manuais não validados em runtime

## Decisões arquiteturais registradas

- ADR-001: Monolito modular (não microsserviços)
- ADR-002: Shared Schema + RLS (não schema por tenant)
- ADR-003: Optimistic Locking em Orders (`@Version`)
- ADR-004: Idempotência em Payments (`idempotency_key`)
- ADR-005: Auditoria via AOP (`@AfterReturning`)
- ADR-006: SUPER_ADMIN via UUID master + Kill Switch duplo

## Próxima ação

**T2** — Controller tests com MockMvc (Claude Code CLI)
Prompt: `PROMPT_T2_CLAUDE_CODE.md`
Branch: `feature/test-rls-integration`
