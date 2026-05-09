# Prompt — Sprint 4a.2: Frontend de Gestão de Catálogo

Branch: `feature/catalog-frontend` (a partir de `develop`).

Pré-requisito: `feature/catalog-backend` mergeada em `develop`
(178 testes verdes).

---

## Contexto

Sprint 4a.1 entregou backend completo de gestão de catálogo:
endpoints de produtos e categorias, soft delete via `deletedAt`,
auto-criação de categoria "Diversos", proteção via
`@RequiresModule("CATALOG_MANAGEMENT")`. Esta sprint entrega a
**UI no PDV** (porta 3000) que consome esses endpoints.

---

## Workflow

**Fase 0 — Investigação curta:** confirmar estado atual do PDV
(layout, sidebar, padrão de modal já existente, padrão de toast,
ApiError consolidado). Reportar antes de codificar.

**Fase 1 — Implementação.** Após aprovação.

**Build mandatório** (`npm run build`).

**Validação manual pelo usuário** (sem simular resultados).

---

## Escopo

### Estrutura de pastas

```
frontend/src/
├── app/
│   └── (pdv)/
│       └── manage/
│           ├── layout.tsx           → guard de role ADMIN
│           ├── products/
│           │   ├── page.tsx
│           │   └── _components/
│           │       └── ProductFormModal.tsx
│           └── categories/
│               ├── page.tsx
│               └── _components/
│                   ├── CategoryFormModal.tsx
│                   └── IconPicker.tsx
├── components/
│   └── ConfirmDialog.tsx            → reusar/criar (verificar se já existe)
└── lib/
    ├── hooks/
    │   ├── useProducts.ts
    │   └── useCategories.ts
    └── catalogIcons.ts              → mapa de ícones disponíveis
```

### Sidebar do PDV — adicionar 2 itens novos

Itens novos visíveis APENAS para `role === 'ADMIN'`:

- **Produtos** → `/manage/products`
- **Categorias** → `/manage/categories`

MANAGER e CASHIER **não veem esses itens** na sidebar. Reusar o
padrão de role-gate que já existe no PDV (se houver).

### Layout `/manage` (`(pdv)/manage/layout.tsx`)

- Client Component
- Verifica `role === 'ADMIN'` lendo `getAuth()`
- Se não for ADMIN, `router.replace('/')` (volta pro home do PDV)
- Estado `isChecking=true` por default; renderiza `null` até
  validar (mesmo padrão do AdminGuard do admin)
- Se for ADMIN, renderiza `{children}`

### Catálogo de ícones (`lib/catalogIcons.ts`)

Mapa fixo de 12 ícones disponíveis. Usar `lucide-react`
(verificar se já está em `package.json`; se não, instalar).

```ts
import {
  Package, Utensils, Coffee, Beer, Pizza, Cake,
  Salad, IceCream, Cookie, Sandwich, Fish, Milk,
} from 'lucide-react';

export const CATALOG_ICONS = {
  'package': Package,
  'utensils': Utensils,
  'coffee': Coffee,
  'beer': Beer,
  'pizza': Pizza,
  'cake': Cake,
  'salad': Salad,
  'ice-cream': IceCream,
  'snack': Cookie,        // lucide não tem 'snack', Cookie cobre
  'sandwich': Sandwich,
  'fish': Fish,
  'milk': Milk,
} as const;

export type CatalogIconKey = keyof typeof CATALOG_ICONS;
```

Helper `getIconComponent(key: string)` que retorna o componente
correspondente, ou `Package` como fallback se a key não existir
(defensivo).

---

## Tela `/manage/categories`

### Lista (estado padrão)

- Header com título "Categorias" e botão **"+ Nova categoria"**
  (vermelho/azul de destaque, à direita)
- Botão secundário **"Mostrar excluídas"** (toggle visual; quando
  ligado, refetch com `?includeDeleted=true`; quando desligado,
  refetch sem o param)
- Lista em formato de tabela ou cards:
  - Coluna **Ícone** (renderizado via `getIconComponent`)
  - Coluna **Nome**
  - Coluna **Produtos** (`productCount` do response)
  - Coluna **Status** (badge "Ativa" verde, "Excluída" cinza)
  - Coluna **Ações** (botões Editar e Excluir)
- Skeleton/spinner durante load
- Erro de carregamento → mensagem inline na área de conteúdo
  (não toast)

### Modal de criar (clique em "Nova categoria")

`<CategoryFormModal mode="create" />`:
- Campo **Nome** (obrigatório, validação inline: 2-100 chars)
- **IconPicker** — grid 4x3 de ícones, clique seleciona, selecionado
  fica destacado (border azul + bg leve)
- Botões **Cancelar** e **Criar**
- Submit: `POST /api/v1/categories` com `{name, icon}`
- Sucesso: toast "Categoria criada", refetch da lista, fecha modal
- Erro 409 (nome duplicado): toast com `body.message`
- Outro erro: toast genérico

### Modal de editar (clique em "Editar")

`<CategoryFormModal mode="edit" category={...} />`:
- Mesmos campos pré-preenchidos
- Submit: `PUT /api/v1/categories/{id}`
- **Categoria com `is_default=true`:** label adicional
  "Categoria padrão" + tooltip "Esta é a categoria padrão do
  sistema". Campo nome continua editável (operador pode renomear
  "Diversos" para outra coisa).

### Excluir (clique em "Excluir")

- Abre `<ConfirmDialog>` com título "Excluir categoria" e
  mensagem "Tem certeza que deseja excluir a categoria '{nome}'?"
- **Categoria com `is_default=true`:** botão "Excluir" desabilitado
  + tooltip "Categoria padrão não pode ser excluída"
- **Categoria com `productCount > 0`:** botão "Excluir" desabilitado
  + tooltip "Categoria com produtos vinculados não pode ser
  excluída"
- Confirmar → `DELETE /api/v1/categories/{id}` → toast de sucesso
  + refetch
- Erro 400 (categoria padrão ou com produtos): toast com mensagem
  do backend

---

## Tela `/manage/products`

### Lista

- Header com título "Produtos" e botão **"+ Novo produto"**
- Botão **"Mostrar excluídos"** (toggle, igual categorias)
- Tabela:
  - **Ícone da categoria** (do `categoryIcon` do response)
  - **Nome**
  - **Categoria** (texto: `categoryName`)
  - **Preço** (formato BR: R$ 0,00)
  - **Status** (badge "Ativo" / "Excluído")
  - **Ações** (Editar / Excluir)
- Skeleton durante load
- Erro inline

### Modal de criar/editar

`<ProductFormModal mode="create|edit" product={...} />`:
- Campo **Nome** (obrigatório, 2-100 chars)
- Campo **Descrição** (opcional, textarea, max 500 chars)
- Campo **Preço** (obrigatório, > 0, máscara BR de moeda — reusar
  `MoneyInput` que já existe no PDV)
- **Select de categoria** (obrigatório):
  - Carrega categorias ativas via hook `useCategories`
  - Cada opção mostra ícone + nome
  - Default: categoria "Diversos" pré-selecionada se for create
- Submit: `POST` ou `PUT /api/v1/products`
- Mesmas regras de toast/refetch das categorias

### Excluir

- ConfirmDialog: "Excluir produto '{nome}'?"
- DELETE → soft delete (backend lida)
- Toast + refetch

---

## Hooks

### `useCategories()`

```ts
function useCategories(options?: { includeDeleted?: boolean }) {
  return {
    categories: CategoryResponse[],
    isLoading: boolean,
    error: ApiError | null,
    refetch: () => void,
    createCategory: (data) => Promise<CategoryResponse>,
    updateCategory: (id, data) => Promise<CategoryResponse>,
    deleteCategory: (id) => Promise<void>,
  };
}
```

Usa `apiFetch`. Não usar React Query nem libs de cache.

### `useProducts()`

Mesmo padrão. Suporta `includeDeleted` no fetch inicial.

---

## ConfirmDialog

Verificar se já existe componente de confirmação no PDV. Se existir,
reusar. Se não, criar `frontend/src/components/ConfirmDialog.tsx`
com props `{ open, title, message, confirmLabel, cancelLabel,
isDangerous, onConfirm, onCancel }`. Padrão equivalente ao
`<ConfirmModal>` do admin.

---

## Padrão de toast

Verificar se já existe sistema de toast no PDV. Se sim, reusar.
Se não, instalar/criar mesmo padrão do admin (Provider em layout
raiz, hook `useToast`). **Não copiar arquivo do admin** — apenas
seguir mesmo contrato.

---

## Cobertura de roles (resumo)

| Ação | ADMIN | MANAGER | CASHIER |
|---|---|---|---|
| Ver itens "Produtos"/"Categorias" na sidebar | ✓ | ✗ | ✗ |
| Acessar `/manage/products` ou `/manage/categories` direto via URL | ✓ | redirect `/` | redirect `/` |

(MANAGER e CASHIER nem entram na tela. Decisão simplifica UI:
não há "modo somente leitura" — backend já protege via 403, frontend
apenas esconde.)

---

## Validação manual (a ser executada PELO USUÁRIO)

Após implementar, declarar "implementação completa, validação
pendente" e listar os 10 cenários abaixo. **Sem simular.**

### Categorias

1. Login como ADMIN → sidebar mostra "Produtos" e "Categorias"
2. Login como MANAGER → sidebar NÃO mostra esses itens. Acesso
   direto a `/manage/categories` redireciona para `/`
3. ADMIN cria categoria nova com nome único → toast sucesso,
   aparece na lista
4. ADMIN tenta criar categoria com nome duplicado → toast de erro
   com mensagem do backend
5. ADMIN edita "Diversos" mudando o nome → salva normalmente
6. ADMIN tenta excluir "Diversos" → botão desabilitado + tooltip
7. ADMIN tenta excluir categoria com produtos → botão desabilitado
   + tooltip
8. ADMIN exclui categoria vazia → some da lista, ativando
   "Mostrar excluídas" reaparece com badge "Excluída"

### Produtos

9. ADMIN cria produto novo: nome, descrição opcional, preço, categoria
   → aparece na lista com ícone da categoria
10. ADMIN exclui produto → some da lista; ativando "Mostrar
    excluídos" reaparece com badge

---

## Fora do escopo

- **Tela de venda do PDV consumindo o catálogo** — sprint futura
  (4b ou 4c)
- **Reordenação de categorias** (`position`) — dívida técnica
  já registrada
- **Upload de imagem** de produto — não combinado pra MVP
- **Variações** (tamanho, cor) — combinamos cada variação =
  produto separado
- **Importação em massa** (CSV/Excel) — futuro
- **Internacionalização**

---

## Princípios

- **Não reinventar componentes existentes.** Reusar
  `ConfirmDialog`, `MoneyInput`, sistema de toast, padrão de
  modal — ou seguir mesmo padrão se não existirem ainda.
- **Mudança mínima na sidebar/layout do PDV.** Adicionar 2
  itens visíveis para ADMIN; não refatorar estrutura existente.
- **Não tocar fora do escopo** (ex: tela de venda, telas de
  caixa). Bug fora do escopo: registrar como recomendação, não
  corrigir.
- **`npm run build` mandatório** antes de declarar concluído.
- **Não simular validação.** Devolver pro usuário com lista de
  10 cenários.

---

## Relatório esperado

**Fase 0:**
- Padrão de toast/modal/ConfirmDialog atual no PDV (existe ou
  precisa criar)
- Padrão de role-gate existente
- Localização real do `MoneyInput` (já existe pelo histórico)
- Versão do `lucide-react` no `package.json`
- Layout/sidebar atual e onde encaixar itens novos

**Fase 1:**
- Lista de arquivos criados/alterados
- Confirmação que `useProducts` e `useCategories` consomem os
  endpoints corretos
- Saída literal de `npm run build`
- Lista dos 10 cenários para o usuário validar
- Desvios da especificação, se houver
