# Prompt — PDV consumindo módulos (Sprint 3a, revisado)

Branch: `feature/pdv-modules-consumer` (a partir de `develop`).

---

## Objetivo

Fazer o frontend PDV (porta 3000) consumir o catálogo de módulos
ativos do tenant logado. Itens de menu de módulos não contratados
ficam escondidos. Páginas correspondentes são protegidas com
fallback amigável.

Endpoint backend já pronto e validado:
`GET /api/v1/me/modules` → retorna `string[]` com códigos ativos
(ex: `["PDV_BASIC", "CATALOG_MANAGEMENT", "REPORTS"]`).

---

## Fase 0 — Investigação (não codifique ainda)

Antes de qualquer linha de código, leia e reporte o estado atual
desses arquivos do **`frontend/`** (não confundir com `admin/`):

1. **`frontend/src/lib/api.ts`** — confirme se `apiFetch` (ou
   equivalente) lida com response 204 No Content. O admin tinha
   bug de `JSON.parse('')` que foi corrigido — verifique se o PDV
   tem o mesmo bug. Se sim, a correção entra como sub-tarefa
   desta sprint.

2. **`frontend/src/lib/auth.ts`** — confirme chaves de
   localStorage. Padrão esperado: `dipdv_token` e `dipdv_user`
   (NÃO `dipdv_admin_token`).

3. **Layout autenticado do PDV** — provavelmente
   `frontend/src/app/(pdv)/layout.tsx` ou equivalente. Identifique
   onde envolver o `ModulesProvider` (mesmo lugar do `AuthGuard`).

4. **Sidebar/menu principal do PDV** — liste os itens existentes
   hoje. Provavelmente: PDV (venda), Caixa, Relatórios, Sair.
   Itens que correspondem a módulos PAID precisarão de
   `<ModuleGate>`.

5. **Versão do Tailwind** no PDV (v3 ou v4). O admin usa v4. Se o
   PDV ainda está em v3, **NÃO migrar nesta sprint** — apenas
   registrar como dívida técnica em `PROJECT_STATE.md`.

**Pare e reporte:**
- Estado de `apiFetch` (trata 204 ou não?)
- Chaves de localStorage no PDV
- Onde fica o layout autenticado
- Lista atual de itens da Sidebar
- Versão do Tailwind no PDV
- Confirmação de que entendeu o escopo

Espere aprovação antes da Fase 1.

---

## Fase 1 — Implementação

### Hook `useModules()`

`frontend/src/lib/hooks/useModules.ts` (criar pasta `hooks` se
ainda não existir).

- Faz `GET /api/v1/me/modules` na primeira renderização do
  usuário autenticado.
- Cacheia em estado global via Context. **Sem React Query nem
  bibliotecas de cache.** Context + useState/useReducer basta.
- Retorna:
  ```ts
  {
    modules: string[],
    isLoading: boolean,
    has(code: string): boolean,
    refetch: () => void
  }
  ```
- `has('REPORTS')` é o método que componentes vão consumir.

Espelhe o padrão de `useTenantModules` que existe no admin
(`admin/src/lib/hooks.ts`) — mesma filosofia, ajustada ao PDV.

### `<ModulesProvider>`

- Envolve o layout autenticado do PDV (mesma camada do AuthGuard).
- Carrega módulos no mount após autenticação confirmada.
- Limpa estado ao deslogar.
- Enquanto `isLoading=true`, layout renderiza skeleton/spinner —
  **não conteúdo nem menus**. Evita flash visual de itens
  aparecendo e sumindo.

### `<ModuleGate>`

`frontend/src/components/ModuleGate.tsx`.

```tsx
type Props = {
  module: string;
  fallback?: ReactNode;
  children: ReactNode;
};
```

- Se `has(module)` retorna true → renderiza `children`
- Caso contrário → renderiza `fallback` (default: `null`)
- Uso duplo: esconder itens de menu (default null) e proteger
  páginas inteiras (fallback customizado)

### `<ModuleNotAvailable>`

`frontend/src/components/ModuleNotAvailable.tsx`.

- Componente simples, centralizado em página inteira
- Mensagem: "Este módulo não está disponível no seu plano."
- Subtexto: "Contate o administrador para mais informações."
- **Sem CTA externo** (não pedir upgrade pelo PDV — assunto
  comercial fora do produto)
- Estilização discreta, mesmo padrão visual do PDV

### Sidebar

Para cada item que corresponde a módulo PAID, envolver em
`<ModuleGate module="CODE">`. Itens BASE (PDV, Caixa) ficam
sempre visíveis sem ModuleGate.

Mapeamento (consulte estado real da Sidebar primeiro):

| Item provável | Módulo | Tier |
|---|---|---|
| PDV / Venda | (sempre visível) | BASE |
| Caixa | (sempre visível) | BASE |
| Relatórios | `REPORTS` | PAID |
| Estoque | `INVENTORY` | PAID |
| WhatsApp | `WHATSAPP_ORDERS` | PAID |
| iFood | `IFOOD_INTEGRATION` | PAID |
| Fidelidade | `LOYALTY` | PAID |

Se a Sidebar atual só tem PDV + Caixa + Relatórios + Sair, aplique
ModuleGate apenas em Relatórios. Demais ficam pra futuras sprints.

### Páginas protegidas

Para cada rota correspondente a módulo PAID (hoje,
provavelmente apenas `/reports`), envolver o conteúdo da página:

```tsx
<ModuleGate
  module="REPORTS"
  fallback={<ModuleNotAvailable />}
>
  <ReportsContent />
</ModuleGate>
```

**Por quê proteger página além do menu?** Defense in depth.
Usuário pode digitar URL direta ou ter bookmark antigo. Backend
já retorna 403, mas frontend mostra mensagem amigável em vez de
erro técnico.

### Logout

Garantir que a função de logout (provavelmente em `lib/auth.ts`)
limpa estado do `ModulesProvider`. Sem isso, próximo login pode
mostrar módulos do usuário anterior por alguns ms (vazamento
visual).

### Sub-tarefa condicional: corrigir `apiFetch` para 204

**Apenas se a Fase 0 detectou o bug.** Aplicar mesma correção
do admin: `safeParseJson` helper + leitura via `text()` antes de
parse. Não copiar arquivo inteiro do admin — adaptar mantendo
contrato existente do PDV.

### Bloqueio de SUPER_ADMIN no PDV

Edge case: SUPER_ADMIN não deveria logar no PDV (ele usa o admin).
Se hoje o `AuthGuard` do PDV permite, ajustar para bloquear:
`role === 'SUPER_ADMIN'` → redirect para `/login` com mensagem
"Use o painel administrativo".

Se essa lógica já existir, apenas confirmar.

---

## Fora do escopo

- **Telas de gestão** (`/manage/products`, `/manage/categories`)
  — sprint 3b
- **Reactividade em tempo real** (websocket/polling para mudanças
  de módulo) — refresh ao recarregar basta no MVP
- **Cache persistente** em localStorage — estado em memória basta
- **Migração Tailwind v3 → v4** se PDV está em v3 — registrar
  como dívida
- **Botão de "atualizar plano" / link comercial**
- **Internacionalização** das mensagens

---

## Validação manual (a ser executada PELO USUÁRIO, não pelo agente)

**O agente não deve listar "Resultados Observados" sem rodar.**
Após implementar, declarar: "implementação completa, validação
pendente do usuário com os 6 cenários abaixo."

**6 cenários** (pré-requisito: backend rodando, admin e PDV
rodando, 2 tenants no banco):

1. Tenant A com `REPORTS` ativo, Tenant B sem.
2. Login no PDV como ADMIN do Tenant A → menu "Relatórios"
   visível, página `/reports` carrega normalmente.
3. Login no PDV como ADMIN do Tenant B → menu "Relatórios"
   escondido. Acesso direto a `/reports` mostra
   `<ModuleNotAvailable>`.
4. No admin, ativar `REPORTS` no Tenant B. Recarregar PDV de B
   ou refazer login → menu agora aparece.
5. No admin, desativar `REPORTS` no Tenant A. Recarregar PDV de A
   → menu some, página mostra fallback.
6. Logout e login com outro usuário → módulos do anterior não
   vazam (sem flash visual).

---

## Workflow

**Fase 0** (investigação): reportar achados, esperar aprovação.

**Fase 1** (implementação): commits atômicos, mensagens em inglês.

**Relatório final** deve incluir:
- Lista de arquivos criados/alterados (paths relativos)
- Confirmar onde `<ModulesProvider>` foi colocado
- Itens da Sidebar protegidos (lista mapeada item → módulo)
- Páginas protegidas (lista rota → módulo)
- Status do `apiFetch` (já tratava 204 ou foi corrigido nesta sprint)
- Status do bloqueio de SUPER_ADMIN no PDV (já existia ou foi adicionado)
- **Declarar validação como "pendente do usuário"** — não simular
  resultados
- Desvios da especificação, se houver

---

## Princípios

- **Não inventar resultados de validação.** Declare "pendente"
  em vez de simular.
- **Investigar antes de codificar** (Fase 0 obrigatória).
- **Não tocar fora do escopo** — Tailwind, refatorações grandes,
  mudanças de UI cosméticas. Bug de fora do escopo: reportar,
  não corrigir.
- **Mudança mínima.** Adicionar consumo de módulos não exige
  refatorar o que já funciona.
