# Prompt — Antigravity: Correção do Histórico Git + Fechamento PR Sprint 2

---

## Contexto

O repositório remoto está incompleto — módulos do Sprint 1 (Order, Inventory)
e configurações críticas (AOP, shared/) existem apenas localmente.
O `PaymentService` depende dessas classes e o CI vai falhar no build.

Estratégia: commitar tudo no branch atual em commits organizados,
depois abrir PR único para `develop`.

**NÃO usar `git add .` em nenhum momento deste prompt.**

---

## Tarefa 1 — Diagnóstico completo

```bash
cd DiPDV
git status
git log --oneline -10
```

Colar o output completo. Precisamos ver:
- Quais arquivos estão `untracked`
- Quais estão `modified`
- Quais commits já existem no branch

---

## Tarefa 2 — Commit 1: Infraestrutura e configurações

Adicionar apenas arquivos de configuração compartilhada:

```bash
git add backend/pom.xml
git add docker-compose.yml
git add .gitignore
git add backend/src/main/java/com/dipdv/shared/
git add backend/src/test/java/com/dipdv/shared/

# Verificar antes de commitar
git diff --cached --stat
```

Esperado no diff: `pom.xml`, `docker-compose.yml`, `.gitignore`,
arquivos de `shared/audit/`, `shared/exception/`, `shared/security/`,
`shared/tenant/`, `shared/config/`.

```bash
git commit -m "chore(infra): dependencias AOP, shared e configuracoes globais

- spring-boot-starter-aop adicionado ao pom.xml
- AuditAspect, AuditLog, Auditable, AuditAction (shared/audit)
- GlobalExceptionHandler com handler OptimisticLock e inet fix
- TenantContextService com UUID_PATTERN
- DataInitializer (perfil dev)
- docker-compose.yml atualizado"
```

---

## Tarefa 3 — Commit 2: Sprint 1 — Módulo Order + Inventory

```bash
git add backend/src/main/resources/db/migration/V5__add_order_item_product_name.sql
git add backend/src/main/java/com/dipdv/modules/order/
git add backend/src/main/java/com/dipdv/modules/inventory/
git add backend/src/test/java/com/dipdv/modules/order/

# Verificar
git diff --cached --stat
```

Esperado: `V5`, entidades `Order/OrderItem/OrderItemModifier`,
`OrderService`, `OrderController`, DTOs, `StockMovement`, `OrderServiceTest`.

```bash
git commit -m "feat(order): modulo de pedidos, itens e estoque — Sprint 1

- Order, OrderItem, OrderItemModifier com precos congelados
- Optimistic Locking @Version + HTTP 409
- OrderService: criar, adicionar item, cancelar, fechar pedido
- Validacao de modificadores: minSelect/maxSelect/maxQuantity
- deductStockForOrder: contrato pronto para PaymentService
- AuditAspect interceptando cancelamento de pedido
- StockMovement + StockMovementType (base para Sprint 2)
- Migration V5: coluna product_name em order_items
- 12 testes OrderServiceTest + 3 AuditAspectTest

Closes #XX (US01.1, US01.3, US01.4, US01.5, US07.1)"
```

---

## Tarefa 4 — Commit 3: Documentação reorganizada

```bash
git add docs/

# Verificar
git diff --cached --stat
```

```bash
git commit -m "docs: reorganizacao da documentacao em subpastas por sprint

- docs/sprints/sprint0/ sprint1/ sprint2/
- ARCHITECTURE.md, DATABASE.md, CONTRIBUTING.md, SETUP.md atualizados"
```

---

## Tarefa 5 — Verificar se ficou algo pendente

```bash
git status
```

Se ainda houver arquivos `untracked` ou `modified` relevantes
(não sendo IDE, logs ou credenciais locais), listar e avaliar
se pertencem ao escopo antes de commitar.

Arquivos que **nunca** devem ser commitados:
- `application-dev.yml` com credenciais locais
- `.idea/`, `*.iml`, `*.class`
- `target/`
- Qualquer `.env`

---

## Tarefa 6 — Rodar testes antes do push

```bash
cd backend
.\mvnw.cmd test
```

Esperado: **55 testes, BUILD SUCCESS**.

Se falhar após adicionar os novos commits, identificar o erro
e corrigir antes de fazer push.

---

## Tarefa 7 — Push e PR

```bash
git push origin feature/US02.1-cashregister-payment-nfce
```

O PR já aberto no GitHub será atualizado automaticamente
com os novos commits.

Verificar no GitHub que o PR agora contém todos os commits:
1. Commit original do Sprint 2
2. `chore(infra): dependencias AOP...`
3. `feat(order): modulo de pedidos...`
4. `docs: reorganizacao...`

---

## Tarefa 8 — Validar CI

Após o push, acessar o GitHub Actions e confirmar:
- Pipeline `CI — Backend` foi disparado
- Build Maven passou sem erro de compilação
- Testes passaram

Colar o link do run do CI no relatório.

---

## Checklist

- [ ] `git status` colado antes de qualquer ação
- [ ] Commit 1 (infra/shared) — `git diff --cached --stat` colado
- [ ] Commit 2 (order/inventory) — `git diff --cached --stat` colado
- [ ] Commit 3 (docs) — se houver alterações
- [ ] `git status` limpo após commits (sem pendências relevantes)
- [ ] `.\mvnw.cmd test` → 55 testes, BUILD SUCCESS
- [ ] `git push origin feature/US02.1-cashregister-payment-nfce`
- [ ] PR atualizado no GitHub com todos os commits
- [ ] CI GitHub Actions → BUILD SUCCESS (colar link do run)

---

## O que NÃO fazer

- Não usar `git add .` — adicionar seletivamente por grupo
- Não commitar `application-dev.yml`, `.idea/`, `target/`
- Não criar branch novo — commitar no branch atual
- Não mergear o PR — aguardar revisão do tech lead
