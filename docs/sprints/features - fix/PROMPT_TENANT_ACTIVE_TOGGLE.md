# Prompt — Micro-sprint: toggle de ativação de tenant

Branch: `feature/tenant-active-toggle` (a partir de `develop`).

Pré-requisito: `feature/pdv-modules-consumer` já mergeada em `develop`.

---

## Contexto

Backend já aceita `active` no `PUT /api/v1/admin/tenants/{id}`
desde a sprint 2a, e existe coluna `active` na tabela desde V1.
Mas a UI do admin não tem controle pra alternar esse estado —
SUPER_ADMIN hoje só consegue desativar tenant editando o banco
diretamente. Esta sprint resolve isso.

Dívida técnica registrada em `PROJECT_STATE.md` desde a sprint
de correção visual do admin.

---

## Escopo

### Backend

`backend/src/main/java/com/dipdv/modules/admin/service/TenantAdminService.java`

Adicionar validação no método `updateTenant`:

- **Constante:** `DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")`
- **Regra:** se `tenantId.equals(DEFAULT_TENANT_ID)` E
  `request.active() == false` → lançar exceção retornando HTTP
  400 com mensagem `"Tenant default não pode ser desativado"`
- Usar o mesmo padrão de exceção já consolidado no projeto
  (provavelmente uma exception customizada tratada pelo
  `GlobalExceptionHandler`)

`backend/src/test/java/com/dipdv/modules/admin/controller/TenantAdminControllerIT.java`

Adicionar 1 teste novo:

- `updateTenant_whenDefaultTenantBeingDeactivated_returns400`
- Faz `PUT /admin/tenants/00000000-...-001` com `active=false`
- Espera status 400 + mensagem específica
- Confirma via DB que `active` continuou `true`

Suite total: 162 → 163 testes verdes.

### Frontend (admin)

`admin/src/app/(admin)/tenants/[id]/page.tsx`

Na seção **"Dados do Tenant"** (existente), adicionar:

- **Badge de status atual** ao lado do título "Dados do Tenant":
  - `Ativo` em verde se `active=true`
  - `Inativo` em cinza/vermelho se `active=false`
- **Botão de ação** abaixo dos campos name/slug, antes do botão "Salvar":
  - Se `active=true` → botão vermelho **"Desativar tenant"**
  - Se `active=false` → botão verde **"Reativar tenant"**
- **Tenant default (UUID `00000000-0000-0000-0000-000000000001`):**
  - Botão **visível mas desabilitado** (`disabled`, `opacity-50`,
    `cursor-not-allowed`)
  - Tooltip via atributo `title`: `"Tenant default não pode ser
    desativado"`
- **Modal de confirmação ao desativar** (não ao reativar):
  - Reusar padrão de modal existente no projeto se houver. Se
    não houver, criar componente simples `<ConfirmModal>` em
    `admin/src/components/`
  - Texto: `"Tem certeza que deseja desativar o tenant '{name}'?
    Usuários deste tenant não conseguirão mais fazer login no
    PDV."`
  - Botões: `Cancelar` (cinza) e `Desativar` (vermelho)

### Comportamento

- **Desativar:** clica no botão → abre modal → confirma → chama
  `PUT /admin/tenants/{id}` com `active: false` → toast de
  sucesso → refetch da página (badge atualiza para Inativo)
- **Reativar:** clica no botão → chama PUT direto (sem modal)
  com `active: true` → toast de sucesso → refetch
- **Erro do backend** (ex: 400 ao tentar desativar default):
  toast de erro com `body.message` do `ApiError`

### O que NÃO mudar

- Campos `name` e `slug` continuam funcionando exatamente como
  estão hoje
- Botão "Salvar" continua salvando name/slug
- Toggle de active **não vai dentro do form do "Salvar"** — é
  ação independente com botão próprio
- Modal de confirmação não usa `window.confirm()` nativo (UX
  inconsistente entre browsers); usar componente React próprio

---

## Validação manual (a ser executada PELO USUÁRIO)

Após implementar, declarar "implementação completa, validação
pendente" e listar os 5 cenários abaixo. **Sem simular
resultados.**

1. **Desativar tenant comum:** acessar edição de tenant ativo
   (não-default) → botão vermelho "Desativar tenant" visível →
   clica → modal abre → confirma → toast de sucesso → badge muda
   pra "Inativo" → botão muda pra "Reativar tenant" verde
2. **Reativar tenant inativo:** mesmo tenant agora inativo →
   clica "Reativar" → sem modal → toast de sucesso → badge volta
   pra "Ativo"
3. **Tenant default protegido (UI):** acessar
   `/tenants/00000000-0000-0000-0000-000000000001` → botão
   "Desativar tenant" visível mas desabilitado → tooltip aparece
   ao hover
4. **Tenant default protegido (backend):** com DevTools Network,
   tentar via console:
   `fetch('/api/v1/admin/tenants/00000000-0000-0000-0000-000000000001',
   {method:'PUT', headers:{'Authorization':'Bearer '+localStorage.getItem('dipdv_admin_token'),'Content-Type':'application/json'}, body:JSON.stringify({active:false})})`
   → resposta 400 com mensagem específica
5. **Soft delete end-to-end:** desativar um tenant qualquer no
   admin → tentar logar no PDV (porta 3000) com usuário daquele
   tenant → backend deve retornar erro de "conta inativa" (esse
   comportamento já existe desde sprints anteriores, vale
   revalidar)

---

## Workflow

**Fase 0 (curta):** confirmar onde está `TenantAdminService` e
`updateTenant`, qual exception customizada usar, se há `<ConfirmModal>`
reusável no projeto. Reportar achados.

**Fase 1 (implementação):** backend + teste novo + frontend.
Commits atômicos.

**Build mandatório:** `mvn test` no backend (163 verdes) e
`npm run build` no admin (sem erros).

**Relatório final:** sem simular validação. Apenas:
- Arquivos criados/alterados
- Confirmação que `<ConfirmModal>` foi reusado ou criado
- Resultado dos builds (verbatim)
- Lista dos 5 cenários para o usuário validar

---

## Princípios

- Não inventar resultados de validação
- Não tocar fora do escopo (não refatorar `TenantForm`, não mexer
  em `name/slug`)
- `mvn test` antes de declarar concluído (backend)
- `npm run build` antes de declarar concluído (frontend)
- Build local é mandatório, não opcional
