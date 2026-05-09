# Prompt — Investigação Forense: Estado de Git e Testes

## Contexto

Estamos investigando uma anomalia. A suite de testes do backend
deveria ter ~164 testes verdes (validado em sprints anteriores).
Um agente trabalhando na branch `feature/catalog-backend`
reportou apenas **86 testes** após uma refatoração que mudou
`active → deletedAt` em DTOs do catálogo.

Possíveis causas (em ordem de probabilidade):
1. Branch criada de base desatualizada (sem merges recentes)
2. Testes deletados ou desabilitados durante refatoração
3. Conflito de merge silencioso que removeu arquivos de teste
4. `mvn test` rodado com perfil/flag que exclui testes
5. Refatoração quebrou testes que ele marcou `@Disabled`

Sua função é investigar **sem corrigir nada** e reportar o
estado real.

---

## Não corrija nada nesta sprint

Repito: **não faça commits, não rebase, não rode `mvn test` em
mais de uma branch para "consertar"**. Apenas observe e reporte.

---

## Verificações solicitadas

### Bloco 1 — Estado das branches

Execute e cole o output literal de cada comando:

```bash
cd backend
git branch -a
git status
git log --oneline --graph --all -30
```

### Bloco 2 — Comparação develop vs feature/catalog-backend

```bash
git log develop --oneline | head -30
git log feature/catalog-backend --oneline | head -30
git merge-base develop feature/catalog-backend
git log --oneline $(git merge-base develop feature/catalog-backend)..develop
git log --oneline $(git merge-base develop feature/catalog-backend)..feature/catalog-backend
```

A última pergunta crítica: a branch `feature/catalog-backend`
tem como base um commit recente de `develop` (depois dos merges
das sprints `chore/tech-debt-cleanup` e
`feature/tenant-active-toggle`)? Ou tem base antiga?

### Bloco 3 — Arquivos deletados na branch

```bash
git diff develop..feature/catalog-backend --stat
git diff develop..feature/catalog-backend --diff-filter=D --name-only
```

Liste especialmente qualquer arquivo `*IT.java` ou `*Test.java`
que tenha sido deletado.

### Bloco 4 — Testes desabilitados

Em ambas as branches:

```bash
git checkout develop
grep -rn "@Disabled" backend/src/test || echo "Nenhum @Disabled em develop"

git checkout feature/catalog-backend
grep -rn "@Disabled" backend/src/test || echo "Nenhum @Disabled em catalog-backend"
```

Compare: `feature/catalog-backend` tem mais `@Disabled` do que
`develop`? Quais classes?

### Bloco 5 — Contagem real de testes em cada branch

Execute em ambas:

```bash
git checkout develop
mvn test 2>&1 | tail -50

git checkout feature/catalog-backend
mvn test 2>&1 | tail -50
```

Cole as últimas 50 linhas de cada execução. Quero ver
`Tests run: X, Failures: Y, Errors: Z` total e por módulo.

Se algum `mvn test` falhar com erro (não apenas testes quebrados,
mas erro de compilação ou similar), cole o erro completo.

### Bloco 6 — Comandos usados pelo agente anterior

Procure por evidências de que o agente anterior rodou `mvn test`
com flags especiais:

```bash
git log feature/catalog-backend --all -p -- pom.xml | head -100
grep -rn "exclude.integration.tests" backend/
grep -rn "skip" backend/pom.xml
```

Há algum perfil ou propriedade que exclui testes por padrão?

### Bloco 7 — Estado atual da branch (sem mexer)

```bash
git checkout feature/catalog-backend
git status
ls -la backend/src/test/java/com/dipdv/modules/catalog/ 2>/dev/null
ls -la backend/src/test/java/com/dipdv/modules/admin/ 2>/dev/null
ls -la backend/src/test/java/com/dipdv/modules/tenant/ 2>/dev/null
ls -la backend/src/test/java/com/dipdv/shared/ 2>/dev/null
find backend/src/test -name "*Test.java" -o -name "*IT.java" | wc -l
```

A última linha é a contagem total de arquivos de teste. Quantos
são?

---

## Restaure ao estado original quando terminar

```bash
git checkout develop
git status
```

Garanta que volta para `develop` e que nada está sujo.

---

## Relatório esperado

Estruture exatamente assim:

```
## Bloco 1 — Estado das branches
[output literal]

## Bloco 2 — Comparação develop vs catalog-backend
[output literal]
**Diagnóstico:** branch criada de base recente (✓) ou
desatualizada (✗)?

## Bloco 3 — Arquivos deletados
[output literal]
**Lista de testes deletados:** [se houver]

## Bloco 4 — @Disabled
[output literal]
**Comparação:** [diferença em quantidade/classes]

## Bloco 5 — Contagem real de testes
develop: Tests run: X, Failures: Y, Errors: Z
catalog-backend: Tests run: X, Failures: Y, Errors: Z

## Bloco 6 — Configuração de Maven
[achados]

## Bloco 7 — Inventário de arquivos de teste
[contagem]

## Diagnóstico final

Causa raiz mais provável da discrepância 164 → 86:
- [opção 1, com evidência]
- [opção 2, com evidência]
- [...]

Recomendação:
- Não execute nada. Apenas recomende.
```

---

## Princípios

- **Não corrija nada.** Apenas observe.
- **Não delete arquivos.**
- **Não faça merge ou rebase.**
- **Não rode comandos destrutivos** (force push, reset --hard, etc).
- **Cole output literal**, não parafraseie.
- **Volte ao estado original** ao final.
