# CHECKPOINT — DiPDV — Fim da Sprint 6 (pré-validação browser)

> Gerado em 2026-05-20 logo após o rebase limpo de `feature/user-management` sobre `develop`. Próximos passos: validação browser dos 15 pontos da Sprint 6, depois Sprint 7 (Deploy Render + PWA mínimo + Bootstrap em produção).

---

## 🚨 AÇÕES IMEDIATAS AO RETOMAR

1. **Force-push da branch reescrita** (se ainda não fez):
   ```bash
   git push --force-with-lease origin feature/user-management
   ```

2. **Validar `/actuator/health` contra o backend NOVO** (o anterior foi falsamente validado contra processo antigo que estava rodando na porta 8080):
   ```bash
   # Garantir que não tem outro Spring rodando
   lsof -i :8080
   # Subir o backend novo
   cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
   # Em outro terminal
   curl http://localhost:8080/actuator/health
   # Esperado: {"status":"UP"}
   ```

3. **Executar os 15 pontos de aceite browser da Sprint 6** (lista completa na seção 5).

4. Após validação verde: **merge `feature/user-management` → `develop`** e iniciar Sprint 7.

---

## 1. Estado atual do repositório

**Branch ativa**: `feature/user-management`  
**HEAD**: `44223da feat(users): add user management sprint 6`  
**Base**: `b5950c1 (develop, origin/develop) Merge branch 'feature/pdv-operacional' into develop`

### Histórico recente em develop
```
b5950c1 (origin/develop, develop) Merge branch 'feature/pdv-operacional' into develop
5da88bd feat(payment): Sprint 5.3 — pagamento com múltiplos meios, idempotência e auditoria
728e9d1 fix(orders): NewOrderDialog UX — drawer closes ao abrir modal, estado resetado
10e70e9 fix(orders): Tipos separados Order/OrderSummary + contexto com GET /orders/{id}
40bfacf fix(order): OrderSummaryResponse com identifier + test
... (mais atrás: 5.2.1, 5.1, 4a.2)
```

### Recovery realizado nesta conversa
Sprint 5 inteira (5.1 + 5.2.1 + 5.2.2 + 5.3) tinha ficado órfã na branch `feature/pdv-operacional`, sem merge para develop. Quando a Sprint 6 começou (`feature/user-management`), foi a partir de develop ainda no estado pós-4a.2 → resultou em test count enganoso (194 ao invés do esperado 208+).

**Etapas de recovery executadas:**
- **Etapa 1**: `git merge --no-ff feature/pdv-operacional` em develop → commit `b5950c1`, 199 testes verdes, pushed
- **Etapa 2**: `git rebase develop` em feature/user-management → 3 conflitos resolvidos manualmente (`AuditAction.java`, `frontend/src/lib/types.ts`, `frontend/src/app/(pdv)/layout.tsx`), `api.ts` auto-mergeado

### Validação pós-rebase (todas verdes)
- ✅ 208 testes (199 develop + 9 Sprint 6)
- ✅ `npm run build` em `frontend/`
- ✅ `npm run build` em `admin/`
- ✅ `git status` limpo

### Pendente
- ⏳ Force-push branch
- ⏳ Validação browser 15 pontos
- ⏳ Validar `/actuator/health` contra processo NOVO
- ⏳ Merge `feature/user-management` → `develop`
- ⏳ Iniciar Sprint 7

---

## 2. Stack do projeto

- **Backend**: Java 21 + Spring Boot 3.3.x + PostgreSQL 16 + Flyway V1-V15 + Testcontainers
- **Frontend PDV**: `frontend/` Next.js 16.2.0 + React 19 + Tailwind v4 raw (porta 3000)
- **Frontend Admin**: `admin/` mesma stack (porta 3001, SUPER_ADMIN only)
- **Test command obrigatório**: `mvn test -Dexclude.integration.tests=""`
- **Backend dev mode**: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- **Branch model**: `develop` → `main`; feature branches por sprint

## 3. Modelo de negócio

SaaS B2B PDV multi-tenant com feature gating.

**Tiers**:
- **Base** (todo tenant): `PDV_BASIC`, `CATALOG_MANAGEMENT`
- **Pago** (SUPER_ADMIN libera por tenant): `PAYMENT_PIX`, `PAYMENT_CARD`, `REPORTS`, `INVENTORY`, `WHATSAPP_ORDERS`, `IFOOD_INTEGRATION`, `LOYALTY`

**Roles**: 
- `SUPER_ADMIN` (cross-tenant, gerencia o SaaS)
- `ADMIN` / `MANAGER` / `CASHIER` (escopo do próprio tenant)

**Credenciais dev** (válidas só em profile dev):
- Tenant default: `00000000-0000-0000-0000-000000000001`
- Master tenant: `ffffffff-ffff-ffff-ffff-ffffffffffff`
- `admin@dipdv.dev / dipdv@2025` (ADMIN)
- `superadmin@dipdv.app / SuperAdmin@2025!` (SUPER_ADMIN)

**Contexto comercial**: Cliente real esperando. Andar rápido SEM comprometer rigor.

---

## 4. Sprint 6 — entregáveis (integrados via rebase)

### Backend
- **V15 migration**: partial unique index em `users (tenant_id, email) WHERE active=true`
- **Endpoint SUPER_ADMIN** (cross-tenant): `POST /api/v1/admin/tenants/{tenantId}/users` — cria primeiro ADMIN do tenant (role fixa em ADMIN)
- **Endpoints ADMIN do tenant** (escopo próprio):
  - `GET /api/v1/users?includeInactive=false` (paginado)
  - `POST /api/v1/users` (cria MANAGER ou CASHIER)
  - `GET /api/v1/users/{id}`
  - `PUT /api/v1/users/{id}` (atualiza name, role, password opcional)
  - `DELETE /api/v1/users/{id}` (soft delete via active=false)
  - `PATCH /api/v1/users/{id}/reactivate`
- **`@Auditable`**: USER_CREATED, USER_UPDATED, USER_DEACTIVATED, USER_REACTIVATED
- **Regras de segurança backend** (não só UI):
  - ADMIN não pode criar/editar com role ADMIN ou SUPER_ADMIN
  - ADMIN não pode desativar a si mesmo (`current.id == target.id` → 409)
  - Tenant isolation via RLS
- **Tests IT**: 9 novos em `UserManagementControllerIT.java` (208 total)

### Frontend Admin (3001)
- `FirstAdminDialog` em `admin/src/components/Tenants/FirstAdminDialog.tsx`
- Modal abre após criar tenant com sucesso
- Campos: email, nome, senha (com toggle mostrar/esconder)
- **Decisão tomada**: cancelar modal **fecha e redireciona para detalhe do tenant**; tenant fica criado SEM ADMIN pendente (estado válido, deferred para badge "Sem ADMIN" em sprint futura)

### Frontend PDV (3000)
- Rota `/manage/users` — guard ADMIN only (MANAGER/CASHIER → redirect `/`)
- Layout: lista + drawer lateral (padrão estabelecido)
- Lista com colunas: nome, email, role badge, status
- Toggle "Ver inativos"
- Drawer:
  - Nome (editável)
  - Email (read-only após criação)
  - Role select: apenas MANAGER e CASHIER aparecem
  - Senha: obrigatório na criação, opcional na edição (vazio = mantém)
  - "Desativar" sem `danger: true` (reversível) / "Reativar" quando inativo
  - Auto-proteção: se editando a si mesmo, botão Desativar disabled + tooltip
- Item "Equipe" no dropdown "Gestão" (depois de Produtos) — apenas para ADMIN

### Hardening pre-deploy
- `application-prod.yml` criado: `show-sql: false`, `ddl-auto: validate`, logging INFO
- `application.yml` (comum) com env vars + fallback dev:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - `JWT_SECRET`, `JWT_EXPIRATION_MS`
  - `CORS_ORIGINS` (lista CSV)
- CORS configurável por env var (lê CSV)
- Spring Boot Actuator + `/actuator/health` sem auth, `show-details: never`
- Frontends: helper `src/lib/api-url.ts` que lê `NEXT_PUBLIC_API_URL` com fallback `http://localhost:8080`
- `frontend/src/lib/api.ts` e `admin/src/lib/api.ts` usam o helper

---

## 5. 15 Pontos de Aceite Browser — PENDENTES

### Admin (3001) — logar como SUPER_ADMIN
1. Criar tenant "Cliente Teste" → modal "Criar primeiro ADMIN" abre → preencher email/nome/senha → toast sucesso, tenant na lista
2. Criar tenant "Outro Teste" + tentar segundo ADMIN com email duplicado no mesmo tenant → toast 409 amigável
3. Cancelar modal sem preencher → tenant criado mas sem ADMIN; redireciona para detalhe do tenant

### PDV (3000) — logar com a conta ADMIN criada no ponto 1
4. Menu "Gestão" → "Equipe" aparece (deve coexistir com OpenOrdersIndicator e CashRegisterIndicator no mesmo nav, sem quebrar layout)
5. Acessar `/manage/users` deslogado → `/login`; como MANAGER → redirect `/`; como ADMIN → tela abre
6. Criar `joana@cliente.com` / Joana / CASHIER / senha "123" → toast, aparece na lista
7. Editar Joana, mudar nome para "Joana Silva", deixar senha vazia → salva, senha não muda
8. Editar Joana, trocar senha para "456" → salva. Logout, login com `joana@cliente.com / 456` → autentica
9. Logado como ADMIN, tentar desativar a própria conta → botão disabled + tooltip
10. Desativar Joana → toast, some da lista ativa
11. Toggle "Ver inativos" → Joana aparece com badge "Inativo", botão vira "Reativar"
12. Reativar Joana → volta para ativa
13. Criar segundo usuário com email duplicado entre ativos no mesmo tenant → toast 409
14. Desativar Joana, criar nova com mesmo email → 201

### Cross-role
15. Login como CASHIER (Joana com a senha "456") → menu "Gestão" NÃO aparece. Digitar `/manage/users` direto na URL → redirect `/`

**Atenção em ponto 4**: o coexistir dos botões. Se quebrar visualmente, registrar como polimento Sprint 7+, não bloqueia.

---

## 6. Sprint 7 — Plano (próxima)

**Escopo consolidado**: Deploy Render + PWA mínimo + Bootstrap em prod

### Decisões já tomadas (não revisitar)
- **Hosting**: Render (familiar pelo usuário). Vercel + Supabase = backup mental
- **Domain**: subdomínio Render no primeiro deploy; custom domain deferred
- **Seeds**: Approach (c) — usuário gera próprio SUPER_ADMIN via SQL pós-deploy, depois deleta seeds dev
- **PWA**: versão mínima (manifest + ícones + SW básico). Full offline-first deferred

### Blocos previstos para o prompt da Sprint 7

#### Bloco A — Containerização e config Render
- `Dockerfile` para backend (ou buildpack Render Java)
- 4 services no Render: backend + frontend PDV + admin + Postgres
- Env vars no painel Render (preencher manualmente):
  - Backend: `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_EXPIRATION_MS`, `CORS_ORIGINS`, `SPRING_PROFILES_ACTIVE=prod`
  - Frontends: `NEXT_PUBLIC_API_URL` apontando para o domínio do backend
- Deploy via integração GitHub
- HTTPS automático (Render fornece)

#### Bloco B — Bootstrap procedure em prod
**O usuário declarou: "vou precisar de auxilio, ainda não trabalhei dessa forma".** O assistant DEVE guiar pelo procedimento, não apenas documentar.

Passos a guiar:
1. Gerar senha forte localmente, salvar em gerenciador de senhas
2. Gerar hash bcrypt:
   ```bash
   pip install bcrypt
   python3 -c "import bcrypt; print(bcrypt.hashpw(b'SENHA_FORTE', bcrypt.gensalt(rounds=12)).decode())"
   ```
3. Acessar console PostgreSQL do Render (psql ou web UI)
4. Verificar tenant master existe e obter ID
5. Rodar INSERT:
   ```sql
   INSERT INTO users (id, tenant_id, email, password_hash, name, role, active, created_at)
   VALUES (
     gen_random_uuid(),
     'ffffffff-ffff-ffff-ffff-ffffffffffff',  -- master_tenant_id
     'real-email@dominio.com',
     '$2b$12$...hash gerado acima...',
     'Seu Nome',
     'SUPER_ADMIN',
     true,
     NOW()
   );
   ```
6. Testar login no admin (porta 3001 em prod) com as credenciais reais
7. Deletar contas dev se existirem em prod (`admin@dipdv.dev`, original `superadmin@dipdv.app`):
   ```sql
   DELETE FROM users WHERE email IN ('admin@dipdv.dev', 'superadmin@dipdv.app');
   ```
8. Confirmar login ainda funciona com a nova conta

#### Bloco C — PWA mínimo
- `frontend/public/manifest.json` (name, short_name, icons, theme_color, background_color, display: "standalone")
- Ícones em `frontend/public/icons/`: 192x192 e 512x512 (podem ser temporários — texto "DiPDV" centralizado, refinar depois)
- Service worker básico em `frontend/public/sw.js`: cache de assets estáticos, network-first para chamadas API
- Registro do SW em `frontend/src/app/layout.tsx`
- Meta tags HTML: `theme-color`, `viewport` com `viewport-fit=cover`
- Link `<link rel="manifest" href="/manifest.json">`
- (Admin painel: PWA opcional, pode pular já que é uso interno)

#### Bloco D — Smoke test fim-a-fim em prod
- Login como SUPER_ADMIN → criar tenant + primeiro ADMIN
- Logout, login como ADMIN do tenant
- Abrir caixa
- Criar comanda
- Adicionar item
- Pagar (CASH)
- Verificar abate de estoque
- "Add to Home Screen" em Android (visualizar PWA funcional)

### Custo estimado Render (informação)
- Free tier: $0 mas serviços dormem (slow first request ~30s)
- Hobby tier: ~$7/serviço/mês → ~$28/mês para 4 services + Postgres
- Recomendação inicial: começar free, migrar para hobby quando cliente confirmar uso real

---

## 7. Convenções do projeto (v1) — usar em prompts futuros

```
- Branch: feature/<nome> a partir de develop ATUALIZADA
- Reuso obrigatório: Page<T>, ApiError, Toast, ConfirmDialog, MoneyInput,
  helpers de src/lib/price.ts, padrão drawer+lista
- Tailwind v4 raw (sem shadcn)
- Soft delete: active=false (Tenants, Users) ou deletedAt (Categories, Products)
- Partial unique index WHERE active/not-deleted para identidade + soft delete
- localStorage: dipdv_token / dipdv_user (PDV); dipdv_admin_token / dipdv_admin_user (admin)
- API URL via helper src/lib/api-url.ts (NEXT_PUBLIC_API_URL com fallback localhost)
- Hooks/contextos estabelecidos: useCashRegister (Sprint 5.1), useOrders (Sprint 5.2)
```

## 8. Disciplina obrigatória nos relatórios do agente

Manter rigor que vem desde Sprint 5:

1. **Últimas 15 linhas literais** de `mvn test -Dexclude.integration.tests=""`
2. **Últimas 15 linhas literais** de `npm run build` (separado para frontend/ e admin/ se ambos afetados)
3. **Output literal** de `git status` (working tree DEVE estar limpo)
4. **Pontos de aceite verbatim** OU declaração explícita "não testado no browser"
5. **Decisões em pontos ambíguos** documentadas
6. **Anti-padrão histórico** (3 ocorrências em 4a.2): agente simula validação sem rodar. **Recusar relatórios sem outputs literais.**

---

## 9. Débito técnico registrado (não atacar agora)

1. Troca de senha NÃO invalida JWT atual do usuário (24h até expirar). Solução futura: `password_version` na tabela User + validação no JWT
2. `categories.position` — sem drag-and-drop UX (hoje só campo numérico)
3. Tailwind v4 `@config` legacy → migrar para `@theme`
4. PROJECT_STATE.md desatualizado — atualizar após fechar Sprint 6
5. PWA full offline-first deferred (apenas mínimo no primeiro deploy)
6. Card de comandas no dashboard (sugestão UX do usuário) — adiado para frente C polimento
7. Tenant pode ficar sem ADMIN se SUPER_ADMIN cancelar `FirstAdminDialog` (estado válido mas precisa badge "Sem ADMIN" no listing de tenants em sprint futura)
8. Sem rate limiting em endpoints de auth (acceptable MVP, futuro com cliente)
9. Sem fluxo "esqueci minha senha" self-service para usuários do tenant (ADMIN reseta manualmente por enquanto)

---

## 10. Lições aprendidas (acumuladas, importantes)

- **Toda sprint encerrada precisa virar merge na develop antes da próxima nascer**. A omissão disso na Sprint 5 causou todo o trabalho de rebase nesta conversa. Aplicar rigorosamente em Sprint 7+.
- **Agentes simulam validação quando CLI-only** — forçar output literal de mvn test / npm run build / git status; recusar relatórios sem isso. 3 episódios em 4a.2, 0 em 5+ depois do hardening de processo.
- **JSONPath filter `[?()]` no MockMvc retorna array mesmo quando match único** — usar `.contains(nullValue())` ou assertion alternativa. Episódio 5.2.1.
- **Spring `Page<T>` envelope** — sempre extrair `.content`; existe tipo reusável em `src/lib/types.ts`.
- **Conversões de unidade vivem em `src/lib/price.ts`** (`apiPriceToCents`, `centsToBRL`, `apiPriceToBRL`) — funções nomeadas matam confusão de unidades. Bug histórico: "R$ 12,50 → R$ 0,13" por dupla divisão por 100.
- **Unique constraint + soft delete = partial unique index** `WHERE active/not-deleted`. Padrão consolidado em V12-V15.
- **Cliente real esperando** — andar rápido mas SEM comprometer rigor de validação. Densidade de prompt sim; disciplina nunca.
- **Health check validado contra processo OLD ≠ validação real** do código novo. Sempre verificar PID/timestamp do processo que responde.
- **Modal/drawer/dropdown overlap** — quando elementos UI novos coexistem (item Gestão + Indicators), validar visualmente o coexistir; auto-merge de código não garante coexistir visual.
- **Conflitos em rebase**: typical em arquivos compartilhados (types.ts, layout.tsx, api.ts, enums centralizados). Combinar é quase sempre a resposta — raro um lado precisa sumir.

---

## 11. Como retomar no próximo chat

### Mensagem inicial sugerida
> Continuando o desenvolvimento do DiPDV. Anexei o checkpoint CHECKPOINT_DIPDV_SPRINT6.md com estado completo, decisões e pendências.
> 
> **Estado atual**: branch `feature/user-management` rebased sobre develop (commit `44223da` em cima de `b5950c1`), 208 testes verdes, builds verdes, working tree limpo. Browser validation dos 15 pontos da Sprint 6 ainda pendente.
> 
> **Próximo passo imediato**: validação browser dos 15 pontos da Sprint 6 (seção 5 do checkpoint).
> 
> **Depois**: merge `feature/user-management` → `develop`, iniciar Sprint 7 (Deploy Render + PWA mínimo + Bootstrap em prod com guia do procedimento).

### Anexar
- Este arquivo (`CHECKPOINT_DIPDV_SPRINT6.md`)
- Opcionalmente o `PROJECT_STATE.md` mais recente se ainda existir como referência adicional (este checkpoint cobre o essencial)

---

## 12. Cenário do primeiro cliente — contexto comercial

- Cliente real esperando deploy
- Onboarding planejado: SUPER_ADMIN (você) cria tenant + primeiro ADMIN via UI (Sprint 6, ✅); ADMIN do cliente cadastra equipe via `/manage/users` (Sprint 6, ✅ pendente browser validation)
- Credenciais do primeiro ADMIN serão enviadas ao cliente pelo canal acordado (WhatsApp/email)
- Trocar senha imediatamente é responsabilidade do cliente (sem força no MVP)
- **Atenção pós-deploy**: o ADMIN do cliente provavelmente vai ter dúvidas operacionais. Considerar produzir um quick-start guide (texto/vídeo curto) cobrindo: abrir caixa, criar comanda, pagar, fechar caixa, criar funcionário, ativar/desativar funcionário. Pode ficar para depois do primeiro deploy.

---

*Documento gerado durante consolidação de checkpoint para reload limpo de contexto.*
