# Prompt — Organização Git Antes dos Testes

## Estado atual
Branch: `feature/US-SA01-super-admin-infra`
6 arquivos modificados + vários untracked.
Tudo relacionado à Sprint 4 Fase 3 (cookie auth + admin frontend).

---

## Passo 1 — Atualizar .gitignore

Antes de qualquer commit, garantir que arquivos de ambiente
nunca entrem no repositório:

```bash
# Verificar conteúdo atual
cat .gitignore
```

Adicionar ao `.gitignore` se ainda não estiver:
```gitignore
# Claude IDE
.claude/

# Arquivos gerados localmente
relatorio-sprint3.pdf
backend/test-result.txt
*.pdf
login.json

# Logs locais
*.log
```

---

## Passo 2 — Commit 1: Backend Sprint 4 Fase 3

Adicionar apenas os arquivos de backend relacionados ao cookie auth:

```bash
git add .gitignore
git add backend/src/main/java/com/dipdv/modules/admin/controller/AdminController.java
git add backend/src/main/java/com/dipdv/modules/admin/repository/AdminRepository.java
git add backend/src/main/java/com/dipdv/modules/admin/dto/AdminLoginRequest.java
git add backend/src/main/java/com/dipdv/modules/admin/dto/AdminLoginResponse.java
git add backend/src/main/java/com/dipdv/shared/exception/GlobalExceptionHandler.java
git add backend/src/main/java/com/dipdv/shared/security/JwtAuthFilter.java
git add backend/src/main/java/com/dipdv/shared/security/SecurityConfig.java

# Adicionar testes novos
git add backend/src/test/java/com/dipdv/modules/admin/controller/
git add backend/src/test/java/com/dipdv/shared/security/JwtAuthFilterTest.java

# Verificar o que está em stage — OBRIGATÓRIO antes de commitar
git diff --cached --stat
```

Esperado: apenas arquivos de backend listados acima.
Se aparecer `.claude/`, `relatorio-sprint3.pdf` ou `login.json` → remover do stage.

```bash
git commit -m "feat(admin): login admin via cookie HttpOnly — Sprint 4 Fase 3

- AdminController: POST /admin/auth/login (seta cookie dipdv_admin_token)
- AdminController: POST /admin/auth/logout (limpa cookie)
- AdminLoginRequest e AdminLoginResponse DTOs
- JwtAuthFilter: le JWT de cookie alem do header Authorization
- SecurityConfig: CORS permite localhost:3001 e admin.dipdv.app
- GlobalExceptionHandler: AccessDeniedException retorna 403 (nao 500)
- AdminControllerSecurityTest e JwtAuthFilterTest"

git push origin feature/US-SA01-super-admin-infra
```

---

## Passo 3 — PR do backend (Sprint 4 completo)

O PR `feature/US-SA01-super-admin-infra` → `develop` agora tem
todas as fases da Sprint 4:
- Fase 1: Migration V8 + infraestrutura SUPER_ADMIN
- Fase 2: Endpoints /admin/**
- Fase 3: Cookie auth

Atualizar a descrição do PR no GitHub com o resumo completo:

```markdown
## Sprint 4 — Módulo SUPER_ADMIN (completo)

### Fase 1 — Infraestrutura
- Migration V8: tenant master, RLS Kill Switch em 15 tabelas
- SUPER_ADMIN role, @PrePersist guard, TenantContextService

### Fase 2 — Endpoints Admin
- 7 endpoints /api/v1/admin/** exclusivos SUPER_ADMIN
- Onboarding atômico, métricas cross-tenant, engajamento

### Fase 3 — Cookie Auth
- Login admin via cookie HttpOnly (sem token no JS)
- JwtAuthFilter lê JWT de cookie + header
- Fix: AccessDeniedException → 403

## Evidências
- 77 testes passando (BUILD SUCCESS)
- Smoke tests: login cookie, endpoints admin, bloqueio 403
```

---

## Passo 4 — Nova branch para admin frontend

```bash
# Garantir que develop está atualizado
git checkout develop
git pull origin develop

# Criar branch dedicada para o admin frontend
git checkout -b feature/US-SA03-admin-frontend
```

---

## Passo 5 — Commit do admin frontend

```bash
# Adicionar o app admin inteiro
git add admin/

# Verificar antes de commitar
git diff --cached --stat
```

Confirmar que apenas a pasta `admin/` está em stage.
**Não deve aparecer:** `backend/`, `.claude/`, arquivos da raiz.

```bash
git commit -m "feat(admin-frontend): app Next.js painel administrativo DiPDV

- Scaffold Next.js 14 com TypeScript + Tailwind (dark theme slate-950)
- middleware.ts: protege rotas via cookie HttpOnly (Edge Runtime SSR)
- lib/api.ts: adminFetch com credentials include, redirect em 401/403
- Tela de login: cookie setado pelo backend, sem token no JS
- /dashboard: metricas globais, top tenants 30 dias
- /dashboard/tenants: lista de clientes com plano e atividade
- /dashboard/engagement: health check ACTIVE|AT_RISK|INACTIVE|NEVER
- Logout limpa cookie via POST /admin/auth/logout"

git push origin feature/US-SA03-admin-frontend
```

---

## Passo 6 — Commit da documentação

```bash
# Ainda na branch feature/US-SA03-admin-frontend ou criar branch docs
git add docs/sprints/sprint4/

git commit -m "docs: relatorios e documentacao Sprint 4"

git push origin feature/US-SA03-admin-frontend
```

---

## Passo 7 — Nova branch para os testes

```bash
git checkout develop
git pull origin develop
git checkout -b feature/test-rls-integration
```

Confirmar estado limpo:
```bash
git status
```
Esperado: `nothing to commit, working tree clean`

---

## Passo 8 — Verificar estrutura de branches

```bash
git branch -a
```

Esperado ao final:
```
* feature/test-rls-integration    ← branch atual para os testes
  feature/US-SA01-super-admin-infra
  feature/US-SA03-admin-frontend
  develop
  main
  remotes/origin/...
```

---

## Arquivos que NUNCA entram em commit

```
.claude/                  ← IDE interno
relatorio-sprint3.pdf     ← gerado localmente
backend/test-result.txt   ← output de teste local
login.json                ← credenciais locais
*.log                     ← logs
```

Se qualquer um aparecer em `git diff --cached`, usar:
```bash
git restore --staged ARQUIVO
```

---

## Checklist

- [ ] `.gitignore` atualizado com `.claude/` e arquivos locais
- [ ] Commit 1 backend (cookie auth) na `feature/US-SA01-super-admin-infra`
- [ ] Push backend e PR atualizado no GitHub
- [ ] Branch `feature/US-SA03-admin-frontend` criada
- [ ] Commit admin frontend na nova branch
- [ ] Push admin frontend
- [ ] Branch `feature/test-rls-integration` criada a partir de `develop`
- [ ] `git status` limpo na branch de testes
