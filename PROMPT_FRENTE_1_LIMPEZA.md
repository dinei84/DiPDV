# Prompt — Frente 1: Limpeza de dívidas técnicas

Branch: `chore/tech-debt-cleanup` (a partir de `develop`).

---

## Contexto

Antes de iniciar a fase de fluxo operacional do PDV (Frente 2),
precisamos pagar dívidas técnicas acumuladas. São pequenas
individualmente, mas afetam usabilidade real (cor de input
invisível) e mascaram bugs reais (warnings de hydration que
desviam atenção em debug).

Lista compilada de `PROJECT_STATE.md` + observações das sprints
anteriores.

---

## Workflow

**Fase 0 — Investigação curta:** confirmar estado atual de cada
item antes de aplicar correções. Reportar achados.

**Fase 1 — Correção:** após aprovação, aplicar item a item em
commits atômicos.

**Build e validação:** `mvn test` no backend, `npm run build` em
ambos frontends. Pedir ao usuário validar manualmente os itens
visuais.

---

## Itens a corrigir

### Item 1 — Cor de texto em inputs do PDV (visual)

**Sintoma:** texto digitado em inputs do PDV aparece com
contraste muito baixo, quase invisível.

**Investigação:**
- Identificar inputs afetados (provavelmente todos os
  `<input>` em formulários do PDV — login, modal de caixa,
  campos de pedido)
- Verificar se a CSS aplicada vem de `globals.css`,
  componente, ou padrão do Tailwind v4 sem override

**Correção:**
- Definir cor de texto explícita em uma das duas opções:
  - Adicionar classes utilitárias em cada input (`text-gray-900`)
  - Adicionar regra global em `globals.css`:
    `input { color: var(--foreground); }` ou similar
- Preferir solução global se afetar muitos componentes;
  preferir classe explícita se afetar apenas alguns

**Validação manual (usuário):** abrir login do PDV, modal de
abertura de caixa, qualquer formulário — confirmar que texto
digitado é claramente visível.

---

### Item 2 — Hydration warnings residuais

**Sintoma:** console mostra "A tree hydrated but some attributes
of the server rendered HTML didn't match the client properties"
em algumas situações no PDV.

**Investigação:**
- Confirmar se o warning persiste em **janela anônima sem
  extensões** — se sumir, é causa externa (extensão tipo
  ColorZilla injetando `cz-shortcut-listen`), e a única ação é
  adicionar `suppressHydrationWarning` ao `<body>` no layout
  raiz do PDV (já existe no admin)
- Se o warning persistir mesmo em anônima, é bug real do código
  — investigar e reportar a causa antes de corrigir

**Correção:**
- Caso confirmado como extensão: adicionar
  `suppressHydrationWarning` ao `<body>` no
  `frontend/src/app/layout.tsx`
- Caso bug real: reportar e esperar nova orientação

---

### Item 3 — `addItem` retorna 200 em vez de 201

**Sintoma:** registrado em `PROJECT_STATE.md`. O endpoint que
adiciona item a um pedido (Order) retorna HTTP 200, mas a
semântica REST correta para criação é 201.

**Investigação:**
- Localizar `OrderController` (ou similar) no backend
- Identificar método `addItem`
- Confirmar que `@ResponseStatus` ou `ResponseEntity` está
  retornando 200

**Correção:**
- Trocar para 201 (`HttpStatus.CREATED`)
- Atualizar testes que esperavam 200 (haver pelo menos um
  esperando o status atual)

**Atenção:** confirmar se algum teste falha após a mudança e
se algum cliente do frontend lê especificamente status 200
(provavelmente não, mas verificar).

---

### Item 4 — `ip_address` em audit_log não populado

**Sintoma:** registrado em `PROJECT_STATE.md`. Coluna
`ip_address` (tipo `inet`) na tabela `audit_log` existe mas é
sempre nula.

**Investigação:**
- Localizar onde audit_log é populado (provavelmente um
  `AuditService` ou interceptor/aspect)
- Confirmar que existe acesso ao IP do request (via
  `HttpServletRequest`)

**Correção:**
- Capturar IP via `request.getRemoteAddr()` ou cabeçalhos
  `X-Forwarded-For` / `X-Real-IP` quando atrás de proxy
- Tratar caso de IPv6 e localhost (`127.0.0.1`, `::1`)
- Persistir no campo `inet`
- Adicionar teste mínimo verificando que o campo é populado
  numa operação auditável

---

## Princípios

- **Não sair do escopo dos 4 itens.** Outros achados →
  reportar como recomendação, não corrigir.
- **Commits atômicos por item.** Mensagens em inglês:
  `fix(pdv): improve input text contrast`,
  `fix(api): return 201 on order item creation`, etc.
- **Build local mandatório** antes de declarar concluído.
- **Sem simulação de validação.** Devolver pro usuário com
  lista de checks visuais (item 1) e funcionais (3, 4).

---

## Relatório esperado

**Fase 0:**
- Item 1: arquivo(s) afetado(s), abordagem proposta
- Item 2: hydration persiste em anônima? (sim/não); causa
  identificada; correção proposta
- Item 3: `arquivo:linha` do método `addItem`; quantos testes
  precisam ser atualizados
- Item 4: onde audit_log é populado; estratégia para captura
  de IP

**Fase 1:**
- Lista de arquivos alterados
- Diff de cada mudança (curtos, não copiar arquivo inteiro)
- Resultado de `mvn test` (backend) e `npm run build`
  (frontend) verbatim
- Validações solicitadas ao usuário (sem simular)
