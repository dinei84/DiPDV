# DiPDV — Estado do Projeto

> Atualizar após cada sprint/entrega. Substitui relatórios longos.
> Última atualização: 2026-05-09

---

## Testes

| Suite | Testes | Status |
|---|---|---|
| Unitários (Services) | 59 | ✅ |
| RLS (Testcontainers) | 10 | ✅ |
| Controllers (HTTP) | 13 (Catalog) | ✅ Catálogo refatorado |
| Controllers Gerais | ~30 | 🔄 T2 em execução |
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
| V9 | RLS Kill Switch para SUPER_ADMIN |
| V10 | Módulos (CATALOG_MANAGEMENT, INVENTORY, ORDERS, etc.) |
| V11 | Refatoração Catálogo: icon + is_default em categories, deleted_at em ambas, backfill "Diversos" |

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

- `last_activity_at` em tenants: update via `OrderService.closeOrder()` ✅
- `ip_address inet` no `AuditLog`: campo ignorado no insert (débito)
- Bug do toggle de módulos resolvido (apiFetch resiliente a 204 No Content) ✅
- **Dívida técnica — categories.position não exposto:**
  Campo existe na tabela (padrão 0) mas não é exposto na API. Implementar reordenação drag-and-drop em sprint futura de polimento de catálogo.
- **Dívida técnica — stock fields removidos da API:**
  `stockQuantity` e `stockMinLevel` foram removidos de ProductRequest/Response pois o módulo INVENTORY não foi implementado. Entidade mantém os campos (padrão 0) para futura habilitação.

### Sprint 3a - Débitos reportados
- **`frontend/src/app/page.tsx`** — Raiz do PDV ainda com template default do Next.js. Causa: não foi consertado durante sprints anteriores. Acessar `localhost:3000/` mostra "To get started, edit the page.tsx" em vez de redirecionar para login. Impacto baixo (navegação real começa em `/login`). Corrigir em sprint de polimento futuro com simples redirect para `/login`.
+ ✅ Resolvido em 08/05/2026: redirect simples para /login

- **PDV — cor de texto em inputs quase invisível.** Inputs nas telas autenticadas do PDV têm contraste muito baixo entre texto digitado e fundo. Validação visual reportada pelo usuário. Corrigir em sprint de polimento visual futura.

## Decisões arquiteturais registradas

- ADR-001: Monolito modular (não microsserviços)
- ADR-002: Shared Schema + RLS (não schema por tenant)
- ADR-003: Optimistic Locking em Orders (`@Version`)
- ADR-004: Idempotência em Payments (`idempotency_key`)
- ADR-005: Auditoria via AOP (`@AfterReturning`)
- ADR-006: SUPER_ADMIN via UUID master + Kill Switch duplo

## Decisões de produto

- **Distribuição de credenciais (decidida em 2026-04-28):**
  Modelo B — convite por link com token. Token tem expiração de 30 dias e é use-once. Sem SMTP no MVP — link enviado manualmente por canal externo (WhatsApp/email). Implementação adiada — primeiro tenant real será o próprio fundador, credenciais hardcoded bastam por enquanto. Sprint específica acontece quando segundo tenant real aparecer.

- **Slug do tenant na edição:**
  Slug é editável manualmente. Não é re-derivado automaticamente do nome após criação. Mudança de slug é decisão consciente do SUPER_ADMIN.

## Próxima ação

**T2** — Controller tests com MockMvc (Claude Code CLI)
Prompt: `PROMPT_T2_CLAUDE_CODE.md`
Branch: `feature/test-rls-integration`
