# Prompt — Admin Frontend (porta 3001)

Leia `AGENTS.md` antes de começar.
Branch: `feature/admin-frontend` (a partir de `develop`).

---

## Objetivo

Criar app Next.js 14 em `admin/` (porta 3001) — painel SUPER_ADMIN.
Consome endpoints já prontos sob `/api/v1/admin/*` e `/api/v1/me/*`.

---

## Stack

Mesmas versões de `frontend/`: Next.js 14 + TypeScript + Tailwind,
App Router, `src-dir`, alias `@/*`. Sem shadcn — raw Tailwind por
consistência com PDV.

---

## Estrutura

```
admin/
├── src/
│   ├── app/
│   │   ├── layout.tsx
│   │   ├── page.tsx                       → redirect / → /login ou /dashboard
│   │   ├── login/page.tsx
│   │   └── (admin)/
│   │       ├── layout.tsx                 → AdminGuard + Sidebar + Header
│   │       ├── dashboard/page.tsx         → placeholder (nome + tenantId + role)
│   │       └── tenants/
│   │           ├── page.tsx               → lista
│   │           ├── new/page.tsx           → form de criação
│   │           └── [id]/page.tsx          → edição + toggles de módulos
│   ├── components/
│   │   ├── AdminGuard.tsx
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   └── ModuleToggle.tsx               → toggle individual de módulo
│   └── lib/
│       ├── api.ts
│       └── auth.ts
├── .env.local.example                     → NEXT_PUBLIC_API_URL=http://localhost:8080
├── next.config.js
├── tailwind.config.ts
└── package.json                           → script "dev" rodando na porta 3001
```

---

## Reuso de `frontend/`

- `lib/api.ts` e `lib/auth.ts`: copiar e adaptar — **mesmo contrato**
  (`apiFetch`, `getAuth`, `saveAuth`, `clearAuth`, `isAuthenticated`).
- **localStorage com chaves separadas:** `dipdv_admin_token` e
  `dipdv_admin_user`. Justificativa: PDV e Admin podem coexistir no
  mesmo navegador.

---

## AdminGuard

- Sem token → redireciona `/login`.
- Token válido mas `role !== 'SUPER_ADMIN'` → limpa storage e
  redireciona `/login` com query `?error=forbidden`.
- Tela de login lê `?error=forbidden` e mostra mensagem
  "Acesso restrito a SUPER_ADMIN".

---

## Login

- Endpoint: `POST /api/v1/auth/login` (mesmo do PDV).
- Sucesso: `saveAuth` → redireciona `/dashboard`.
- Mensagem de erro genérica em caso de credencial inválida.
- Tenant desativado retorna 403 do backend → mostrar mensagem clara.

---

## Tela de tenants — lista (`/tenants`)

- `GET /api/v1/admin/tenants` → tabela.
- Colunas: nome, slug, status (ativo/inativo), nº de módulos ativos,
  ações (editar).
- Botão "Novo tenant" → `/tenants/new`.
- Linha clicável → `/tenants/[id]`.

## Tela de criação (`/tenants/new`)

- Form: name (obrigatório), slug (opcional, autopreenchido a partir do
  name no blur).
- Submit: `POST /api/v1/admin/tenants` → redirect para `/tenants/[id]`
  do tenant recém-criado.

## Tela de edição (`/tenants/[id]`)

Duas seções na mesma página:

**Seção 1 — Dados do tenant**
- Form: name, active (toggle).
- Botão "Salvar" → `PUT /api/v1/admin/tenants/{id}`.

**Seção 2 — Módulos**
- `GET /api/v1/admin/modules/catalog` → lista de todos os módulos
  disponíveis (com tier).
- `GET /api/v1/admin/modules/tenants/{id}` → módulos atualmente ativos.
- Renderizar lista agrupada por tier (BASE em cima, PAID embaixo).
- Toggle por módulo:
  - BASE: toggle desabilitado/cinza com tooltip "Módulo essencial,
    sempre ativo".
  - PAID: toggle interativo. Mudança chama `POST .../enable` ou
    `.../disable`.
- Feedback otimista + rollback em erro.

---

## Sidebar

Itens: Dashboard, Tenants, Sair.
Header: nome do usuário + role.

---

## Smoke test (manual)

1. `cd admin && npm run dev` → sobe na porta 3001.
2. Acesso sem token → `/login`.
3. Login com ADMIN comum → bloqueado, mensagem "Acesso restrito".
4. Login com `superadmin@dipdv.app / SuperAdmin@2025!` → `/dashboard`.
5. `/tenants` lista os tenants existentes.
6. Criar tenant novo → aparece na lista.
7. Editar tenant → toggle de PAID liga/desliga; BASE bloqueado.
8. Desativar tenant → tentar logar como ADMIN dele no PDV → backend 403.

---

## Fora do escopo

- Paginação / busca / filtros na lista de tenants.
- Gerenciamento de usuários do tenant.
- Métricas no dashboard (placeholder basta).
- i18n, dark mode, responsividade mobile (foco desktop).
- Testes automatizados (smoke manual basta).

---

## Relatório esperado (minimalista)

- Lista de arquivos criados (paths relativos a `admin/`).
- Versões instaladas: next, react, tailwindcss, typescript.
- Smoke test: ✓ ou ✗ para cada um dos 8 itens.
- Desvios da especificação, se houver.
