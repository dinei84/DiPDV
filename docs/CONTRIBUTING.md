# Guia de Contribuição — DiPDV

> Padrões de Git Flow, commits, Pull Requests e revisão de código.

---

## Git Flow

O projeto segue o **Git Flow clássico simplificado**, adaptado para sprints de 2 semanas.

```
main          ← produção estável (tag por sprint: v0.1.0, v0.2.0...)
  │
develop       ← integração contínua (branch padrão)
  │
  ├── feature/US01.1-abrir-pedido
  ├── feature/US02.1-registrar-pagamento
  └── hotfix/corrigir-calculo-troco    ← parte direto de main
```

### Regras de branch

| Branch | Origem | Merge para | Proteção |
|---|---|---|---|
| `main` | `develop` (Sprint Review) | — | ✅ PR obrigatório + CI verde |
| `develop` | `main` | `main` | ✅ PR obrigatório + CI verde |
| `feature/*` | `develop` | `develop` | — |
| `hotfix/*` | `main` | `main` + `develop` | — |

### Nomenclatura de branches

```bash
# Features (vinculadas a User Stories do backlog)
feature/US01.1-abrir-pedido
feature/US02.3-fechamento-de-caixa
feature/US06.2-autenticacao-jwt

# Hotfixes (corrigem bugs em produção)
hotfix/calculo-troco-incorreto
hotfix/rls-vazamento-tenant

# Chores (tarefas técnicas sem US vinculada)
chore/configurar-github-actions
chore/adicionar-swagger
```

---

## Padrão de Commits

O projeto adota **Conventional Commits** — padrão amplamente usado no mercado, compatível com geração automática de CHANGELOG.

### Formato

```
<tipo>(<escopo>): <descrição curta em minúsculas>

[corpo opcional — explica o porquê, não o quê]

[footer opcional — referências, breaking changes]
```

### Tipos

| Tipo | Quando usar |
|---|---|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `refactor` | Refatoração sem mudança de comportamento |
| `test` | Adição ou correção de testes |
| `docs` | Alteração em documentação |
| `chore` | Tarefas de build, CI, dependências |
| `perf` | Melhoria de performance |
| `style` | Formatação, indentação (sem lógica) |

### Escopos sugeridos

`auth`, `catalog`, `order`, `payment`, `cashregister`, `inventory`, `report`, `tenant`, `audit`, `security`, `infra`, `docs`

### Exemplos

```bash
# Boa descrição — explica a mudança
feat(order): implementar abertura de pedido com seleção de itens

fix(payment): corrigir validação de idempotency_key duplicada

refactor(tenant): extrair TenantContext para ThreadLocal isolado

test(order): adicionar teste de conflito com optimistic locking

docs(database): atualizar diagrama ER com tabela stock_movements

chore(infra): configurar GitHub Actions para CI do backend

# Referenciando issue/US do backlog
feat(catalog): adicionar suporte a grupos de modificadores

Implementa CRUD de modifier_groups e modifier_options.
Vincula grupos a produtos via tabela associativa product_modifier_groups.

Closes #23 (US03.3)
```

---

## Pull Requests

### Antes de abrir um PR

- [ ] Branch atualizada com `develop` (`git rebase develop`)
- [ ] Testes passando localmente (`mvn test`)
- [ ] Sem conflitos de merge
- [ ] Código formatado (sem warnings no IDE)
- [ ] Swagger atualizado se novos endpoints foram criados

### Template de PR

```markdown
## O que este PR faz?
Breve descrição da mudança.

## User Story relacionada
Closes #XX (US##.#)

## Tipo de mudança
- [ ] Nova funcionalidade
- [ ] Correção de bug
- [ ] Refatoração
- [ ] Documentação

## Como testar?
1. Passo 1
2. Passo 2

## Checklist
- [ ] Testes unitários adicionados/atualizados
- [ ] Documentação atualizada se necessário
- [ ] Migrations compatíveis com dados existentes
- [ ] Sem segredos ou dados sensíveis no código
```

### Regras de revisão

- Todo PR para `develop` exige **ao menos 1 aprovação**
- CI deve estar verde (build + testes)
- PRs com mais de 400 linhas alteradas devem ser quebrados em PRs menores
- Comentários de revisão devem ser resolvidos antes do merge

---

## CI/CD

### Pipeline de CI (GitHub Actions)

**A cada push em `feature/*` e PR para `develop`:**
1. Build Maven (`mvn compile`)
2. Testes unitários (`mvn test`)
3. Análise estática (opcional: Checkstyle)

**A cada merge em `develop`:**
1. Build + testes
2. Deploy automático no Railway (ambiente staging)

**A cada merge em `main`:**
1. Build + testes
2. Deploy automático no Railway (ambiente produção)
3. Tag de versão gerada automaticamente

### Convenção de versões (SemVer)

```
v{major}.{minor}.{patch}

v0.1.0  → Sprint 0 concluído (fundação técnica)
v0.2.0  → Sprint 1 concluído (PDV funcional)
v0.3.0  → Sprint 2 concluído (caixa e estoque)
v1.0.0  → MVP completo (Sprint 3 concluído)
```

---

## Checklist de Sprint Review

Ao final de cada sprint, antes do merge `develop → main`:

- [ ] Todos os critérios de aceitação das USs do sprint atendidos
- [ ] Cobertura de testes ≥ 70% nas camadas de serviço
- [ ] Swagger atualizado com novos endpoints
- [ ] DATABASE.md atualizado se houve mudanças no schema
- [ ] ARCHITECTURE.md atualizado se houve decisões arquiteturais
- [ ] Tag de versão criada no GitHub
- [ ] Release notes publicadas no GitHub Releases
