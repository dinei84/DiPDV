# SPRINT 6.1 — Correções pós-validação browser da Sprint 6

> Branch atual: `feature/user-management` (rebased sobre develop em `44223da` em cima de `b5950c1`).
> 4 problemas identificados na validação browser da Sprint 6. Esta sprint resolve todos antes do merge → develop.
> **Continuar na mesma branch.** Commits adicionais, sem branch nova.

---

## 🚨 Pré-requisitos ao começar

1. **Confirmar estado de partida**:
   ```bash
   cd backend && mvn test -Dexclude.integration.tests=""
   # Esperado: 208 verdes
   cd ../frontend && npm run build
   # Esperado: verde
   cd ../admin && npm run build
   # Esperado: verde
   git status
   # Esperado: working tree limpo, HEAD em 44223da na feature/user-management
   ```

2. **Se force-push da branch ainda estiver pendente do checkpoint anterior**:
   ```bash
   git push --force-with-lease origin feature/user-management
   ```

3. **Validar `/actuator/health` contra o backend NOVO** (não esquecer):
   ```bash
   lsof -i :8080  # garantir nenhum Spring antigo rodando
   cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
   # em outro terminal
   curl http://localhost:8080/actuator/health
   # Esperado: {"status":"UP"}
   ```

---

## Anexar a este prompt
- `CHECKPOINT_DIPDV_SPRINT6.md` (contexto técnico completo)
- `PROJECT_STATE.md` (decisões e padrões consolidados)
- Este documento (escopo 6.1)

---

## Decisões já tomadas (não revisitar)

1. **Estratégia de login: email globalmente único** (das 4 opções discutidas — global, resolução automática, subdomínio, slug — adotada a global por menor risco e UX mais limpa para MVP).
2. **Sem branch nova** — commits seguem em `feature/user-management`.
3. **Matriz de permissões** consolidada abaixo, derivada do PROJECT_STATE.
4. **Migration nova: V16** (subindo de V15 da Sprint 6).
5. **Disciplina de relatórios** segue idêntica ao padrão Sprint 5+ (outputs literais obrigatórios).

---

## Matriz de permissões consolidada

| Recurso                                  | ADMIN | MANAGER | CASHIER |
|------------------------------------------|-------|---------|---------|
| Tela inicial (home operacional)          | ✅    | ✅      | ✅      |
| Catálogo — categorias + produtos         | ✅    | ❌      | ❌      |
| Equipe (`/manage/users`)                 | ✅    | ❌      | ❌      |
| Relatórios (`/reports`)                  | ✅    | ✅      | ❌      |
| Caixa — visualizar status                | ✅    | ✅      | ✅      |
| Caixa — abrir / fechar / sangria         | ✅    | ✅      | ❌      |
| Comandas — listar + lançar item          | ✅    | ✅      | ✅      |
| Comandas — cancelar pedido               | ✅    | ✅      | ❌      |
| Pagamento (cash, PIX)                    | ✅    | ✅      | ✅      |
| Gestão de tenants/módulos (admin/3001)   | SUPER_ADMIN exclusivo                |

Camadas de enforcement:
- **Backend**: `@PreAuthorize("hasRole('X')")` ou `hasAnyRole(...)` nos controllers
- **Client-side route**: componente `<RoleGuard>` em cada rota gated
- **Client-side botão**: check via `useAuth()` ou similar para esconder/desabilitar ações específicas

Botão escondido sem backend protegido = falsa segurança. Backend protegido sem UI gated = UX ruim. Ambos obrigatórios.

---

## Bug 1 — Login do PDV exige tenant_id (UUID) no formulário

### Sintoma
Tela de login do PDV (3000) tem campo `tenant_id` obrigatório. Onboarding quebrado: SUPER_ADMIN cria tenant + primeiro ADMIN (Sprint 6 ponto 1), envia credenciais ao cliente, mas o cliente não consegue logar sem o UUID do tenant. O cliente real não tem como saber esse UUID.

### Diagnóstico
A V15 criou `unique(tenant_id, email) WHERE active=true` (email único *dentro* do tenant). Login precisava do tenant_id para desambiguar. UX inaceitável para MVP.

### Solução
**Email globalmente único.** Login = email + senha, sem tenant_id.

### Backend

- **Migration V16** (`backend/src/main/resources/db/migration/V16__user_email_global_unique.sql`):
  ```sql
  -- Diagnóstico defensivo: detectar colisões cross-tenant antes de migrar
  -- Se houver duplicatas de email entre tenants ativos, a migration deve falhar para investigação manual.
  DO $$
  DECLARE
      duplicate_count INTEGER;
  BEGIN
      SELECT COUNT(*) INTO duplicate_count
      FROM (
          SELECT email FROM users WHERE active = true GROUP BY email HAVING COUNT(*) > 1
      ) duplicates;

      IF duplicate_count > 0 THEN
          RAISE EXCEPTION 'V16 abortada: % emails duplicados entre tenants ativos. Investigar antes de prosseguir.', duplicate_count;
      END IF;
  END $$;

  -- Drop do índice antigo (V15) e recriação como global único
  DROP INDEX IF EXISTS idx_users_tenant_email_active;
  CREATE UNIQUE INDEX idx_users_email_active ON users (email) WHERE active = true;
  ```
  Nome exato do índice da V15 a confirmar no código antes de aplicar o DROP.

- **`LoginRequest` DTO**: remover campo `tenantId` (se houver). Manter apenas `email` + `password`.

- **`AuthController.login()`**: receber só email + senha. Resolver tenant via novo método do `UserRepository.findByEmailAndActiveTrue(String email)`. Tenant_id continua sendo claim no JWT (a sessão segue tenant-aware).

- **`UserRepository`**: adicionar `Optional<User> findByEmailAndActiveTrue(String email)`.

- **`UserService.createUser()`** e **`UserService.createFirstAdmin()`**: validação de email duplicado agora é global (não mais por tenant). Mensagem 409 amigável: "Já existe um usuário ativo com este email".

- **Tests IT** a atualizar/criar:
  - `AuthControllerIT`: cenários de login sem tenant_id (sucesso); login com email inexistente (401).
  - `UserManagementControllerIT`: 409 quando ADMIN tenta criar funcionário com email já ativo *em outro tenant* (não só no próprio).
  - `AdminTenantControllerIT`: 409 quando SUPER_ADMIN tenta criar primeiro ADMIN com email já ativo em qualquer tenant.

### Frontend PDV (`frontend/`, porta 3000)

- Tela de login (`src/app/login/page.tsx` ou similar): remover input `tenant_id`. Form passa a ter só email + senha.
- `src/lib/api.ts` ou `src/lib/auth.ts`: chamada de login envia `{ email, password }`, sem `tenantId`.
- Verificar se algum lugar do PDV usa tenant_id pré-login (não deve).

### Frontend Admin (`admin/`, porta 3001)

- Tela de login: verificar se pedia tenant_id (provavelmente não pedia, já que SUPER_ADMIN vive no master tenant). Confirmar que está consistente.
- O admin/3001 segue gerenciando tenants e seus módulos com tenant_id explícito — comportamento esperado, NÃO mudar.

### Aceites browser (Bug 1)

- [ ] Login com `admin@dipdv.dev` / `dipdv@2025` → autentica, redireciona pra home do PDV. Form NÃO mostra campo tenant_id.
- [ ] Login com `superadmin@dipdv.app` / `SuperAdmin@2025!` no admin/3001 → autentica.
- [ ] SUPER_ADMIN cria tenant "Cliente Teste" + primeiro ADMIN com email `cliente@teste.com` (Sprint 6 ponto 1) → logout, login no PDV com `cliente@teste.com` → autentica sem precisar de tenant_id.
- [ ] SUPER_ADMIN tenta criar primeiro ADMIN de outro tenant com mesmo `cliente@teste.com` → toast 409 amigável.

---

## Bug 2 — Drawer de criação de funcionário mostra tenant_id no campo email

### Sintoma reportado
> "quando estou logado como admin do tenant, quando eu vou criar um usuário, ele traz o tenant no lugar do email, e quando eu substituo o tenant pelo email que vou cadastrar para o usuário, quando tento logar com aquele usuário, não dá certo, as credenciais não ficam válidas"

Dois sub-bugs combinados:
1. **Visual**: campo email do drawer vem preenchido com o UUID do tenant.
2. **Funcional**: mesmo após o ADMIN substituir, o login do funcionário criado não funciona.

### Diagnóstico
Investigar `frontend/src/app/(pdv)/manage/users/` (drawer/form de criação de funcionário). Hipóteses ordenadas por probabilidade:

1. **Hipótese A — defaultValue errado**: input email tem `defaultValue={user.tenantId}` ou `value={user.tenantId}` por copy-paste ou typo. ADMIN substitui no UI, mas o handler pode estar pegando o valor antigo do state.
2. **Hipótese B — payload destructurado errado**: o handler de submit monta `{ email: tenantId, name, role, password }` por bug, e o backend grava o tenant_id no campo email. Daí o login falha porque o email cadastrado não é o que o ADMIN digitou.
3. **Hipótese C — controlled vs uncontrolled**: input não está totalmente controlled; o React mantém o valor antigo e ignora a edição do ADMIN.

### Solução
- Ler o componente do drawer de funcionários (provavelmente `frontend/src/app/(pdv)/manage/users/UserDrawer.tsx` ou similar).
- Identificar a fonte do tenant_id no campo email e corrigir.
- Garantir: campo email vazio na criação, controlled component, payload `{ email, name, role, password }` envia o email do input (não o tenant_id).
- Validar via Network tab do browser: payload do POST `/api/v1/users` tem o email correto.

### Aceites browser (Bug 2)
- [ ] Logar como ADMIN → `/manage/users` → "Novo funcionário" → drawer abre com campo email **vazio**.
- [ ] Preencher `joana@cliente.com` / Joana / CASHIER / senha "123" → toast sucesso.
- [ ] Verificar no Network tab que o payload enviado é `{"email":"joana@cliente.com","name":"Joana","role":"CASHIER","password":"123"}` — SEM tenant_id no payload.
- [ ] Logout, login com `joana@cliente.com` / `123` → autentica. Repete os pontos 4 e 5 da Sprint 6 de cara que falharam no teste browser anterior.

---

## Bug 3 — Role gating ausente em rotas e botões

### Sintoma reportado
> "a role de cashier tem alguma coisa errada, porque estou conseguindo visualizar tudo e editar tudo, e não deveria ser assim certo"

CASHIER consegue acessar `/manage/categories`, `/manage/products`, `/reports` via URL direta. A Sprint 6 só implementou role guard em `/manage/users` — faltou nas rotas anteriores.

### Solução

#### Backend — auditar `@PreAuthorize` em todos os controllers

Auditar e adicionar onde faltar:
- `CategoryController` (todos métodos de mutação): `@PreAuthorize("hasRole('ADMIN')")`
- `ProductController` (todos métodos de mutação): `@PreAuthorize("hasRole('ADMIN')")`
- `ReportsController`: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`
- `CashRegisterController.open(...)`, `close(...)`, `addCashIn(...)`, `addCashOut(...)`: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`
- `CashRegisterController.getCurrent(...)` e similares (visualização): liberado (ADMIN/MANAGER/CASHIER autenticados)
- `OrderController.cancel(...)`: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`
- `OrderController.*` listagem/criação/adição de item: liberado para ADMIN/MANAGER/CASHIER
- `UserManagementController.*`: já tem (`@PreAuthorize("hasRole('ADMIN')")`) — confirmar

**Tests IT obrigatórios** (cada um valida 403 quando role errada e 2xx quando role correta):
- `CategoryControllerIT.cashierCannotCreate_returns403`
- `ProductControllerIT.cashierCannotUpdate_returns403`
- `ReportsControllerIT.cashierCannotAccess_returns403`
- `CashRegisterControllerIT.cashierCannotOpen_returns403`
- `CashRegisterControllerIT.managerCanOpen_returns2xx`
- `OrderControllerIT.cashierCannotCancel_returns403`
- `OrderControllerIT.managerCanCancel_returns2xx`

#### Frontend — componente `<RoleGuard>`

Criar `frontend/src/components/RoleGuard.tsx` (ou estender o `AuthGuard` existente com prop `roles`):
- Recebe prop `roles: Role[]`
- Lê role do usuário do hook `useAuth()` / contexto
- Se role do usuário não estiver no array → redireciona `/` com toast informativo (ou silenciosamente, decisão do agente; sugestão: toast "Acesso não autorizado" para clareza)
- Padrão de loading `isChecking` igual ao `AuthGuard` para evitar flash de conteúdo

Aplicar em:
- `/manage/categories` → `<RoleGuard roles={["ADMIN"]}>`
- `/manage/products` → `<RoleGuard roles={["ADMIN"]}>`
- `/manage/users` → confirmar que já tem
- `/reports` → `<RoleGuard roles={["ADMIN", "MANAGER"]}>`

Header (`frontend/src/components/Header.tsx` ou similar):
- Dropdown "Gestão" — só renderiza se role === "ADMIN"
- Item "Relatórios" — só renderiza se role in ["ADMIN", "MANAGER"]

Botões com role check (esconder/desabilitar conforme role):
- Botão "Abrir caixa" / "Fechar caixa" — só para ADMIN/MANAGER
- Botão "Cancelar pedido" — só para ADMIN/MANAGER

### Aceites browser (Bug 3)
- [ ] Login como CASHIER (joana, senha "123") → header NÃO mostra dropdown "Gestão" nem item "Relatórios".
- [ ] CASHIER digita `/manage/categories` na URL → redirect `/`.
- [ ] CASHIER digita `/manage/products` → redirect `/`.
- [ ] CASHIER digita `/manage/users` → redirect `/` (já era esperado da Sprint 6).
- [ ] CASHIER digita `/reports` → redirect `/`.
- [ ] CASHIER acessa tela de comandas (`/orders` ou equivalente) → vê lista, mas botão "Cancelar pedido" não aparece.
- [ ] CASHIER acessa caixa → vê status, mas botão "Abrir/Fechar caixa" não aparece.
- [ ] Login como MANAGER (criar um se ainda não existe) → consegue `/reports`, consegue ver botão "Fechar caixa", consegue cancelar pedidos. NÃO consegue `/manage/products`.
- [ ] Tentar chamar `POST /api/v1/products` com JWT de CASHIER via curl ou Postman → 403.

---

## Bug 4 — Navegação placeholder errada

### Sintoma reportado
> "quando clico no DiPDV (a home) ele leva para a home do dashboard onde ficariam os relatórios e gráficos. e quando eu clico no PDV ele leva para a edição das categorias, vamos ter que melhorar esse fluxo"

Logo "DiPDV" → dashboard de relatórios. Botão "PDV" do menu → `/manage/categories`. Ambos placeholder errados.

### Solução

#### Definir a "home" temporária do PDV

Investigar o que existe hoje:
- Existe rota `/orders` (lista de comandas, Sprint 5.2)? Provavelmente sim, dado o `OpenOrdersIndicator` mencionado nas convenções.
- Existe rota `/` definida em `frontend/src/app/page.tsx`? Para onde redireciona hoje?

Decisão a tomar pelo agente, com justificativa documentada:
- **Opção 1**: home = `/` → renderiza lista de comandas abertas (reusa componente da Sprint 5.2). Mais informativa, melhor UX. Recomendada se a tela de comandas já tiver layout adequado para ser "home".
- **Opção 2**: home = `/` → placeholder mínimo com texto curto ("Bem-vindo ao DiPDV. Use o menu para Comandas, Caixa, Relatórios, Gestão. Tela de venda em construção (Sprint futura).") + atalhos para as rotas principais. Mais simples, sem reuso de componente.

Preferência: Opção 1 se viável; Opção 2 como fallback. Cards de comandas com mudança de cor por tempo NÃO são escopo desta sprint (deferred conforme relatório do cliente).

#### Ajustes no Header

- **Logo "DiPDV"** → href `/` (home operacional definida acima).
- **Botão "PDV"** do menu → **remover**. Justificativa: a tela de venda do PDV ainda não existe (Sprint 4b adiada). Manter o botão apontando pra qualquer lugar incorreto cria confusão. Quando a tela existir (Sprint futura), o item é reintroduzido apontando para a rota correta.
- Resto dos menus permanece: Gestão (dropdown, ADMIN only), Caixa, Comandas, Relatórios (ADMIN/MANAGER), com role gating do Bug 3.

### Aceites browser (Bug 4)
- [ ] Click no logo "DiPDV" no header → leva para a home operacional definida (lista de comandas ou placeholder). NÃO leva para relatórios.
- [ ] Botão "PDV" não aparece mais no menu.
- [ ] Layout do header coexiste sem quebra com OpenOrdersIndicator + CashRegisterIndicator + dropdown Gestão (ponto 4 da Sprint 6 que falava sobre coexistência).
- [ ] Logout → login → cai na home operacional, não em relatórios.

---

## Re-validação dos 15 pontos da Sprint 6

Após corrigir os 4 bugs, **re-executar os 15 pontos de aceite** da Sprint 6 (seção 5 do `CHECKPOINT_DIPDV_SPRINT6.md`), com as seguintes adaptações:

- Pontos 1, 2, 3 (admin/3001) — sem mudança esperada
- Ponto 6 — criar Joana sem precisar de tenant_id no payload do form
- Ponto 8 — login com `joana@cliente.com / 456` deve funcionar (agora sem tenant_id)
- Ponto 15 — CASHIER (Joana) NÃO vê "Gestão" (já era), e adicionalmente NÃO vê Relatórios; URL direta de `/manage/users`, `/manage/categories`, `/manage/products`, `/reports` → redirect `/`

---

## Backlog explicitamente registrado (NÃO atacar nesta sprint)

Itens do relatório do cliente que viraram débito técnico:
- **Hard delete de funcionário** pelo ADMIN do tenant (cliente confirmou "fica anotado") — sprint futura
- **Filtro de duplicados na criação** de admin/funcionário (cliente disse "não é tão importante, precisamos discutir") — sprint futura
- **Cards de comandas com mudança de cor por tempo** (cliente disse "outra sprint") — frente C de polimento

Adicionar esses itens ao `PROJECT_STATE.md` na seção de débito técnico ao final desta sprint.

---

## Disciplina obrigatória no relatório final

(Padrão consolidado desde Sprint 5; 3 ocorrências de simulação em 4a.2 → 0 desde o hardening.)

1. **Últimas 15 linhas literais** de `cd backend && mvn test -Dexclude.integration.tests=""` — esperado: 208 + novos testes do Bug 3 (≥ 215 total).
2. **Últimas 15 linhas literais** de `cd frontend && npm run build` — verde.
3. **Últimas 15 linhas literais** de `cd admin && npm run build` — verde.
4. **Output literal** de `git status` — working tree limpo.
5. **Output literal** de `git log --oneline feature/user-management ^develop` — commits da sprint legíveis.
6. **Aceites verbatim** das 4 seções de Bug acima + re-validação dos 15 da Sprint 6. Para cada item, marcar `[x]` (testado, passou), `[ ]` (não testado), ou `[FAIL]` (testado, falhou — abrir issue).
7. **Decisões em pontos ambíguos** documentadas (especialmente: Opção 1 vs 2 da home; hipótese A vs B vs C do Bug 2; nome final do índice na V16).
8. **Recusar relatórios sem outputs literais.** Padrão duro.

Não declarar verde sem rodar os comandos. Não declarar testado no browser sem ter testado de fato. Não declarar implementado sem ter commitado e pushado.

---

## Commit hygiene

- Sugestão de granularidade (não obrigatória, mas organiza):
  - `feat(auth): V16 + email global unique + login sem tenant_id`
  - `fix(users): drawer de funcionário envia email correto no payload`
  - `feat(security): RoleGuard + @PreAuthorize em catálogo, relatórios e caixa`
  - `fix(ui): logo → home operacional; remove botão PDV placeholder`
- Migration V16 em commit dedicado ou junto com o feat de auth — não misturar com bugs não relacionados.
- Push final só depois de tudo verde (mvn test + builds + browser).

---

## Pós-sprint

1. Merge `feature/user-management` → `develop` (`--no-ff` com mensagem clara).
2. Atualizar `PROJECT_STATE.md`:
   - Sprint 6 ✅ + Sprint 6.1 ✅ na seção de roadmap.
   - Decisão "email globalmente único" na seção de identidade.
   - Backlog: hard delete, filtro de duplicados, cards de comanda com timer de cor.
   - Lição aprendida: "Validação browser real revela problemas que outputs CLI verdes não capturam — não confundir 208 testes verdes com aceite funcional."
3. Iniciar Sprint 7 (Deploy Render + PWA mínimo + Bootstrap em prod) — escopo já planejado no checkpoint anterior, seção 6.

---

## Resumo executivo do escopo

| Bug | Severidade | Esforço estimado | Camadas afetadas |
|-----|------------|-------------------|---------------------|
| 1 — Login sem tenant_id | Bloqueador | Médio (migration + backend + 2 frontends + tests) | DB, Auth, Frontend PDV, Frontend Admin |
| 2 — Drawer com tenant_id no email | Bloqueador | Baixo (investigação + fix pontual) | Frontend PDV |
| 3 — Role gating ausente | Bloqueador (segurança) | Médio-alto (RoleGuard + auditoria backend + tests IT) | Backend + Frontend PDV |
| 4 — Navegação placeholder | Polimento alto | Baixo | Frontend PDV |

Tudo em uma única sprint. Após esta sprint, branch sai do limbo e vira merge → develop.
