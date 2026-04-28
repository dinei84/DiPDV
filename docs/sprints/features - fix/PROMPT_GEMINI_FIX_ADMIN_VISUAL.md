# Prompt — Correção Visual do Admin (Gemini CLI)

Leia `AGENTS.md` antes de começar.
Branch: `fix/admin-visual-bugs` (a partir de `develop`).

---

## Contexto

A sprint anterior (admin frontend) foi entregue funcionalmente,
mas a validação manual revelou que o CSS não está sendo aplicado
corretamente — botões e tabelas renderizam como HTML cru, sem
Tailwind. Há também outros 3 bugs.

Você tem **autonomia para investigar** antes de aplicar correções.
**Reporte o que encontrou ANTES de executar.** Vamos seguir o
mesmo modelo de checkpoint usado nas sprints anteriores: investigar,
reportar, esperar aprovação, então corrigir.

Imagens dos bugs estão em anexo nesta conversa, se disponíveis.

---

## Bugs reportados (em ordem de prioridade)

### Bug 1 (CRÍTICO) — Tailwind/CSS não aplicado

**Sintomas observados:**
- Sidebar com itens "Dashboard" e "Tenants" renderizados como
  links de texto azul-sublinhado padrão HTML
- Botão "Sair" sem estilo (visual de `<button>` cru)
- Layout sem grid/flex — Sidebar, Header e conteúdo amontoados
- Tabelas sem espaçamento e sem hierarquia tipográfica
- Toggles renderizando como `<input type="checkbox">` nativo

**Hipóteses prováveis (investigar e confirmar):**
- `globals.css` não importado em `app/layout.tsx`
- `tailwind.config.ts` com `content` apontando para path errado
  (precisa pegar `src/**/*.{ts,tsx}` ou similar)
- `postcss.config.mjs` mal configurado
- Falta a diretiva `@tailwind base; @tailwind components;
  @tailwind utilities;` em `globals.css`

**Não corrigir antes de reportar a causa raiz.**

### Bug 2 — Hydration error

**Sintomas:** console mostra "A tree hydrated but some attributes
of the server rendered HTML didn't match the client".

**Hipóteses (investigar):**
- Atributo `cz-shortcut-listen="true"` no `<body>` (extensão de
  browser ColorZilla — pode ser falso positivo, não bug do código)
- Algum componente client lendo `localStorage` antes de hidratar
  (faltando `mounted` guard)
- `AdminGuard` ou `Toast` renderizando diferente entre server e
  client

Reporte se o erro persiste em janela anônima sem extensões — se
sim, é bug do código; se não, é extensão e podemos ignorar.

### Bug 3 — Slug não atualiza ao renomear tenant

**Sintoma:** ao editar nome do tenant na seção 1 e salvar, o slug
permanece o original.

**Decisão de produto (já tomada):**
- Slug é **editável manualmente**, não auto-derivado do nome após
  criação
- Comportamento correto:
  - Na **criação** (`/tenants/new`): slug autopreenche no blur do
    nome (já funciona)
  - Na **edição** (`/tenants/[id]`): slug é campo editável também,
    mas **não é alterado automaticamente** quando o nome muda
    (mudança de slug é decisão consciente do SUPER_ADMIN —
    mudar slug pode quebrar URLs/integrações futuras)

**Investigue se hoje a edição mostra o campo slug e se está editável.**
Se não estiver visível ou não estiver editável, esse é o ajuste.

### Bug 4 — Mensagem "Acesso restrito" não aparece

**Sintoma:** quando ADMIN comum tenta logar, é redirecionado para
`/login?error=forbidden` mas a mensagem não é exibida.

**Hipóteses (investigar):**
- Tela de login não está lendo o query param `?error=forbidden`
- Está lendo mas o componente de mensagem não está sendo renderizado
- A query string está sendo apagada antes de a mensagem renderizar

---

## Tarefa adicional (não-código)

Atualizar `PROJECT_STATE.md` na raiz com duas decisões registradas:

```markdown
## Decisões de produto

- **Distribuição de credenciais (decidida em <data>):**
  Modelo B — convite por link com token. Token tem expiração de
  30 dias e é use-once. Sem SMTP no MVP — link enviado manualmente
  por canal externo (WhatsApp/email). Implementação adiada — primeiro
  tenant real será o próprio fundador, credenciais hardcoded
  bastam por enquanto. Sprint específica acontece quando segundo
  tenant real aparecer.

- **Slug do tenant na edição:**
  Slug é editável manualmente. Não é re-derivado automaticamente do
  nome após criação. Mudança de slug é decisão consciente do
  SUPER_ADMIN.
```

Se o arquivo `PROJECT_STATE.md` ainda não existe, criá-lo com
estrutura mínima: Stack, Roadmap atual, Decisões de produto,
Dívidas técnicas.

---

## Workflow esperado

**Fase 1 — Investigação (não corrija ainda):**
1. Investigar cada um dos 4 bugs
2. Identificar causa raiz com referência a arquivo:linha
3. Para cada bug, propor correção concreta (qual arquivo muda,
   o que muda)
4. Se encontrar bugs adicionais não listados, reportar antes de
   corrigir
5. **Reportar tudo e parar.** Esperar aprovação.

**Fase 2 — Correção (após aprovação):**
1. Aplicar correções um bug por vez, commits atômicos
2. Validar visualmente após cada correção (descrever o que aparece
   na tela)
3. Atualizar `PROJECT_STATE.md` com as duas decisões
4. Reportar o que foi feito

---

## Princípios

- **Investigue antes de corrigir.** Não chute hipótese e mude
  config sem confirmar a causa.
- **Reporte bugs adicionais que descobrir**, sem corrigir
  silenciosamente. Pode haver outros sintomas do mesmo bug-mãe.
- **Não inflar escopo.** Os 4 bugs listados + atualização do
  PROJECT_STATE. Outros achados viram recomendação no relatório,
  não correção nesta sprint.
- **Commits atômicos** com mensagens em inglês.

---

## Relatório de investigação esperado (Fase 1)

Para cada bug:

```
## Bug N — <nome>

**Causa raiz:** <descrição com arquivo:linha>
**Correção proposta:** <o que muda, em qual arquivo>
**Risco/efeito colateral:** <se houver>
```

Mais:
- Bugs adicionais encontrados (se houver), no mesmo formato
- Recomendação de ordem de correção
