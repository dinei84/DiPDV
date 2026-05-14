# Sprint 4a.2 — Prompt 1: Infra do PDV + Tela de Categorias

## Contexto
- Branch: `feature/catalog-frontend` (já criada)
- Fase 0 concluída: gaps confirmados — `lucide-react`, `ApiError`, `Toast`, `MoneyInput`, `ConfirmDialog` não existem no PDV
- Backend pronto: `/api/v1/categories` funcional, suporta `?includeDeleted=true`
- Admin (porta 3001) tem `Toast` e `ApiError` implementados — usar como referência de padrão (não copiar cego: localStorage keys e contextos são diferentes)

## Escopo
Infra do PDV + tela `/manage/categories`. Produtos vai para o Prompt 2 desta sprint.

## Entregáveis

### 1. Infra
- **lucide-react**: `npm install` em `frontend/`
- **ApiError tipado** (`src/lib/api-error.ts`): classe tipada espelhando admin. Atualizar o wrapper de fetch (`src/lib/api.ts`) para:
  - 401 → toast de erro + logout + redirect `/login`
  - 403 → toast com a mensagem de módulo do backend (campo `module` ou `message`)
  - Demais erros → toast genérico com `error.message` ou fallback
- **Toast** (`src/lib/toast/`): Context + Provider, bottom-right, dedup por mensagem, limite 3 simultâneos. Provider plugado em `app/layout.tsx` **antes** do `AuthGuard`. Expor API imperativa (`toast.error(...)`, `toast.success(...)`) para uso fora de hooks (necessário no wrapper de fetch).
- **MoneyInput** (`src/components/MoneyInput.tsx`): estado interno em **centavos (integer)** para evitar ponto flutuante. Display via `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`. Props: `value: number /* centavos */`, `onChange: (cents: number) => void`. Sem dependência externa de máscara. **Não usado nesta tela — deixar pronto para o Prompt 2.**
- **ConfirmDialog** (`src/lib/confirm/`): API **Promise-based**, `const ok = await confirm({ title, message, danger?: boolean })`. Provider centralizado para evitar múltiplos modais declarativos. Render: overlay modal centralizado.

### 2. Sidebar
- Criar grupo **"Gestão"** colapsável no header/sidebar atual
- Renderizado **apenas se `role === 'ADMIN'`** — validar antes de montar o grupo
- Conteúdo agora: "Categorias". "Produtos" entra no Prompt 2 — deixar `// TODO: prompt 2` comentado no lugar

### 3. Tela `/manage/categories`
- Path: `src/app/(pdv)/manage/categories/page.tsx`
- Guard: redirect para `/` se `role !== 'ADMIN'` (não basta esconder no menu)
- Layout: **Lista + Drawer lateral (Sheet)**. Sem modais nesta tela — exceto o `ConfirmDialog` global.
- Toggle **"Ver inativos"** no topo (default off) → adiciona `?includeDeleted=true` no GET
- Lista: ícone (lucide), nome, ordem de exibição, badge "Inativa" se `deletedAt`
- Botão "Nova categoria" → abre drawer vazio
- Click em item → abre drawer preenchido
- Drawer:
  - Campo **Nome**
  - **Picker de ícone** entre os 12: `package, utensils, coffee, beer, pizza, cake, salad, ice-cream, snack, sandwich, fish, milk`
  - Campo numérico **"Ordem de exibição"** → mapeia `position` do DTO
  - **Salvar** → toast de sucesso
  - **Excluir** (somente se ativo e não for "Diversos") → `await confirm({ danger: true })` antes
  - **Reativar** (somente se `deletedAt != null`) → substitui o botão Excluir quando aplicável
- Regras de UX para retornos do backend:
  - "Diversos" não excluível: botão Excluir desabilitado preventivamente + tooltip; se backend recusar, toast claro
  - Categoria com produtos: backend recusa → capturar e mostrar toast com a mensagem

## Diretrizes técnicas (não negociáveis)
- localStorage keys do PDV: `dipdv_token`, `dipdv_user` (admin usa outras — não confundir)
- Tailwind v4 raw — **sem** shadcn, **sem** outras libs de UI
- Sem `alert()` / `confirm()` nativos do browser em ponto algum
- Toast Provider antes do AuthGuard em `app/layout.tsx`

## Validação antes de reportar
**Obrigatório:**
- `npm run build` em `frontend/` — output literal no relatório, deve passar sem erros nem novos warnings

**Recomendado** (se rodar, reportar verbatim; **não simular**):
- `npm run dev` + smoke test manual dos pontos de aceite abaixo

## Pontos de aceite (validação manual pelo usuário)
1. Login como `admin@dipdv.dev` → menu "Gestão" visível, "Categorias" abre a tela
2. Acessar `/manage/categories` com usuário não-ADMIN → redirect para `/`
3. Criar, editar e excluir uma categoria comum → toasts apropriados em cada caso
4. Tentar excluir "Diversos" → bloqueado com feedback claro
5. Toggle "Ver inativos" → mostra soft-deleted; botão "Reativar" funciona
6. Forçar erro 401 (token inválido no localStorage) → toast + logout automático
7. Forçar erro 403 em endpoint protegido por módulo → toast com mensagem do módulo

## Relatório (minimalista)
- Arquivos criados/alterados (lista)
- Output literal do `npm run build`
- Decisões tomadas em pontos ambíguos (justificadas em 1–2 linhas cada)
- Desvios da spec, se houver
- **Se a validação manual foi rodada pelo agente**: output observado por ponto. **Se não foi**: declarar explicitamente "não testado no browser".

## Anti-padrões a evitar
- Listar "Resultados Observados" sem ter rodado o app
- Declarar "implementado" sem commit
- Copiar arquivos do admin sem adaptar (chaves de localStorage, paths, contextos)
