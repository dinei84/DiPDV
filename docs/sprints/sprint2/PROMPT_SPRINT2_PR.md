# Prompt — Antigravity: Fechamento PR Sprint 2

---

## Contexto

Sprint 2 validada com 55 testes e todos os smoke tests confirmados.
O worktree tinha alterações pendentes e o commit não foi feito ainda.
Este prompt organiza e fecha o PR corretamente.

---

## Tarefa 1 — Auditar o estado atual do repositório

```bash
cd backend
git status
git diff --stat
git log --oneline -5
```

Colar o output completo antes de fazer qualquer coisa.
Precisamos ver exatamente o que está pendente.

---

## Tarefa 2 — Classificar as alterações

Com base no `git status`, separar os arquivos em três grupos:

**Grupo A — Pertencem ao Sprint 2 (entram no commit):**
- `V7__add_payment_cash_register_id.sql`
- `MockNfceService.java` (corrigido)
- `MockNfceServiceTest.java` (novo)
- `AuditLog.java` (corrigido — ip_address)
- Qualquer arquivo de CashRegister, Payment ou NFC-e ainda não commitado

**Grupo B — Alterações de ambiente (NÃO entram no commit):**
- `application-dev.yml` com credenciais locais
- Arquivos de configuração do IDE (`.idea/`, `*.iml`)
- Qualquer arquivo de log ou build

**Grupo C — Arquivos não identificados:**
- Listar e avaliar caso a caso antes de incluir

---

## Tarefa 3 — Fazer o commit do Sprint 2

Adicionar apenas os arquivos do Grupo A:

```bash
# Adicionar seletivamente — NÃO usar git add .
git add src/main/resources/db/migration/V7__add_payment_cash_register_id.sql
git add src/main/java/com/dipdv/modules/nfce/service/MockNfceService.java
git add src/main/java/com/dipdv/shared/audit/AuditLog.java
git add src/test/java/com/dipdv/modules/nfce/service/MockNfceServiceTest.java

# Adicionar os demais arquivos do Sprint 2 ainda pendentes
git add src/main/java/com/dipdv/modules/cashregister/
git add src/main/java/com/dipdv/modules/payment/
git add src/main/java/com/dipdv/modules/nfce/
git add src/test/java/com/dipdv/modules/cashregister/
git add src/test/java/com/dipdv/modules/payment/

# Verificar o que será commitado
git diff --cached --stat
```

Confirmar que apenas arquivos do Sprint 2 estão em stage antes de commitar.

```bash
git commit -m "feat(payment): CashRegister + Payment + NFC-e mock — Sprint 2

- CashRegister: abertura/fechamento de turno, sangria/suprimento
- CashRegister: @Auditable no fechamento → audit_log confirmado
- PaymentService: fluxo completo CASH e PIX com idempotência
- PaymentService: integração com deductStockForOrder (Sprint 1)
- PaymentService: disparo automático de NFC-e após PAID
- MockNfceService: chave de acesso 44 dígitos corrigida
- Migration V6: tabela nfce_documents com RLS
- Migration V7: coluna cash_register_id em payments
- AuditLog: ip_address ignorado no insert (compatibilidade inet)
- 55 testes passando (53 unitários + MockNfceServiceTest + contextLoads)

Evidências validadas:
- Idempotência: mesmo UUID retornado em requests duplicados
- NFC-e: accessKey com 44 dígitos confirmada em runtime
- audit_log: CASH_REGISTER_CLOSED registrado
- 409 confirmado ao tentar pagar com caixa fechado

Closes #XX (US02.1, US02.2, US02.3, US02.4)"

git push origin feature/US02.1-cashregister-payment-nfce
```

---

## Tarefa 4 — Abrir o PR

Acessar o GitHub e abrir o PR:

**De:** `feature/US02.1-cashregister-payment-nfce`
**Para:** `develop`

**Título:**
```
feat(payment): CashRegister + Payment + NFC-e mock — Sprint 2
```

**Descrição:**
```markdown
## O que este PR faz
Implementa o fluxo completo de venda do PDV:
abertura de caixa → pedido → pagamento → NFC-e automática → fechamento.

## User Stories
Closes #XX (US02.1, US02.2, US02.3, US02.4)

## Tipo de mudança
- [x] Nova funcionalidade
- [x] Correção de bug (3 bugs encontrados em runtime e corrigidos)

## Bugs corrigidos
1. `payments.cash_register_id` ausente no schema → Migration V7
2. `accessKey` NFC-e com tamanho inválido → MockNfceService corrigido
3. `ip_address inet` incompatível com Hibernate → AuditLog corrigido

## Evidências de runtime
- 55/55 testes passando com Docker ativo
- Idempotência: mesmo UUID em requests duplicados
- NFC-e: accessKey com 44 dígitos confirmada
- audit_log: CASH_REGISTER_CLOSED registrado
- 409 ao tentar pagar com caixa fechado

## Checklist
- [x] Testes passando (55)
- [x] Swagger atualizado
- [x] Migrations V6 e V7 executadas
- [x] 3 bugs corrigidos com testes cobrindo os cenários
- [x] Sem segredos ou dados sensíveis
```

---

## Tarefa 5 — Verificar o develop

Após o PR aberto, confirmar que o `develop` está atualizado com o Sprint 1:

```bash
git log --oneline origin/develop -5
```

Se o PR do Sprint 1 (`feature/US01.1-order-module`) ainda não foi mergeado,
fazer o merge antes do Sprint 2 para evitar conflitos:

```
develop ← feature/US01.1-order-module  (Sprint 1 — merge primeiro)
develop ← feature/US03.3-modifier-groups (Sprint 1 Modifiers)
develop ← feature/US02.1-cashregister-payment-nfce (Sprint 2)
```

---

## Checklist

- [x] `git status` colado antes de qualquer ação
- [x] Apenas arquivos do Sprint 2 em stage (`git diff --cached --stat`)
- [x] `git commit` com mensagem completa
- [x] `git push origin feature/US02.1-cashregister-payment-nfce`
- [x] PR aberto no GitHub com link colado no relatório
- [x] `develop` atualizado com Sprint 1 antes do merge do Sprint 2

---

## O que NÃO fazer

- Não usar `git add .` — adicionar seletivamente
- Não commitar `application-dev.yml` com credenciais locais
- Não commitar arquivos do IDE (`.idea/`, `*.iml`, `*.class`)
- Não mergear o PR — aguardar revisão do tech lead
