# Prompt — Correção Git Status + Branch Sprint 4

## Estado atual
Branch: `feature/US05.1-reports-dashboard`
Arquivos modificados pelo Gemini (frontend fixes) ainda não commitados.
Branch do Sprint 4 ainda não existe.

---

## Passo 1 — Commit dos frontend fixes (branch atual)

Adicionar seletivamente apenas os arquivos do fix:

```bash
git add frontend/src/lib/api.ts
git add frontend/src/app/page.tsx
git add frontend/src/app/layout.tsx
git add frontend/src/app/(auth)/login/page.tsx
git add frontend/src/app/(pdv)/layout.tsx
git add frontend/src/app/(pdv)/reports/page.tsx
git add frontend/src/app/globals.css
git add frontend/src/components/AuthGuard.tsx

git diff --cached --stat
```

Confirmar que apenas esses 8 arquivos estão em stage, depois commitar:

```bash
git commit -m "fix(frontend): correcoes de estabilidade e segurança de rotas

- apiFetch: tratamento de 401 com redirect para /login e mensagem
- apiFetchBlob: novo helper para download PDF autenticado
- RootPage: redireciona para /pdv ou /login conforme autenticação
- AuthGuard: route guard client-side protegendo rotas do PDV
- layout.tsx: suppressHydrationWarning (extensões de browser)
- (pdv)/layout.tsx: envolto em AuthGuard
- reports/page.tsx: SSR-safe date via useEffect
- login/page.tsx: exibe mensagem de sessão expirada via query param"

git push origin feature/US05.1-reports-dashboard
```

---

## Passo 2 — Commit da documentação

```bash
git add docs/README.md
git add docs/setup/
git add docs/sprints/sprint3/
git add docs/sprints/sprint4/
git add docs/sprints/FIX/
git add docs/sprints/next_step/

git commit -m "docs: documentacao de sprints 3 e 4 e guias de setup"

git push origin feature/US05.1-reports-dashboard
```

Arquivos que **não entram** em nenhum commit:
- `.claude/` — diretório interno do IDE
- `relatorio-sprint3.pdf` — arquivo gerado localmente

---

## Passo 3 — Abrir PR do Sprint 3 (se ainda não estiver aberto)

Verificar se o PR já existe:
```bash
git log --oneline origin/develop..HEAD
```

Se houver commits pendentes de merge, abrir PR no GitHub:
- **De:** `feature/US05.1-reports-dashboard`
- **Para:** `develop`
- **Título:** `feat(report): Relatórios + Dashboard + Frontend fixes — Sprint 3`

---

## Passo 4 — Criar branch do Sprint 4

Após confirmar que os commits do Sprint 3 estão no remoto:

```bash
# Atualizar develop local
git checkout develop
git pull origin develop

# Criar branch do Sprint 4
git checkout -b feature/US-SA01-super-admin-infra

# Confirmar
git branch
git status
```

Esperado: branch `feature/US-SA01-super-admin-infra` limpa, sem arquivos pendentes.

---

## Checklist

- [ ] 8 arquivos de frontend fix commitados em `feature/US05.1-reports-dashboard`
- [ ] Documentação commitada separadamente
- [ ] `.claude/` e `relatorio-sprint3.pdf` fora do commit
- [ ] Push feito para o remoto
- [ ] PR do Sprint 3 aberto ou atualizado
- [ ] Branch `feature/US-SA01-super-admin-infra` criada a partir de `develop`
- [ ] `git status` limpo na nova branch
