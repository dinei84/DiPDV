# SPRINT 6.2 — Hotfix: navegação de comanda fora de /pdv

> Branch sugerida: `fix/order-navigation`, criada a partir de `develop` (commit `d8fbbe3` ou mais recente).
> Sprint pequena, cirúrgica. Apenas frontend. Sem migration, sem mudança de backend.

---

## Contexto

Validação manual no browser pelo dono do produto revelou: o usuário consegue ver o indicador "N comandas abertas" no header de qualquer tela (`/manage/products`, `/manage/categories`, `/manage/users`, `/reports`). Ao clicar no indicador, o `OpenOrdersDrawer` abre normalmente sobreposto à tela atual. Ao **clicar numa comanda da lista**, no entanto, o drawer fecha e o usuário continua na tela em que estava (ex: gerenciamento de produtos), com nenhum sinal visual de que algo aconteceu.

Diagnóstico (já confirmado via Network tab):
- O clique chama `setCurrentOrderId(id)` no `OrdersContext`.
- O `useEffect` do Context dispara `GET /api/v1/orders/{id}` → **resposta 200 OK**.
- O `currentOrder` é atualizado no Context corretamente.
- **Problema**: o `CurrentOrderPanel` (que renderiza a comanda atual) só existe dentro de `frontend/src/app/(pdv)/pdv/page.tsx`. Em qualquer outra rota, ele não está montado, e a mudança no Context fica invisível pro usuário.

O mesmo padrão de bug afeta a **criação de comanda nova fora de `/pdv`**: clicar em "+ Nova comanda" no indicador do header (`OpenOrdersIndicator`) abre o `NewOrderDialog`. Submeter cria a comanda no backend e seta `currentOrderId` no Context (linha 93 do `OrdersContext`), mas o usuário continua na tela onde estava, sem ver a comanda recém-criada.

---

## Escopo

Dois fixes pontuais, ambos no frontend PDV, ambos do mesmo padrão (forçar navegação para `/pdv` quando uma comanda é selecionada/criada fora de `/pdv`).

### Fix 1 — `OpenOrdersDrawer.handleSelectOrder`

Arquivo: `frontend/src/components/Orders/OpenOrdersDrawer.tsx`

Hoje:
```tsx
const handleSelectOrder = (id: string) => {
  setCurrentOrderId(id);
  onClose();
};
```

Desejado:
```tsx
const handleSelectOrder = (id: string) => {
  setCurrentOrderId(id);
  onClose();
  router.push('/pdv');
};
```

Mais o import e hook:
```tsx
import { useRouter } from 'next/navigation';
// ...
const router = useRouter();
```

Comportamento: se o usuário **já estava** em `/pdv`, o `router.push('/pdv')` é no-op de URL (Next.js detecta mesma rota) — mas pode disparar re-render leve. Aceitável. Alternativa: condicionar com `usePathname()` e só pushar se não estiver em `/pdv` — fica a critério do agente, ambas são corretas; **preferência: incondicional, é mais simples e o no-op não tem efeito colateral perceptível**.

### Fix 2 — Navegação após criar comanda nova

A criação de comanda pelo `NewOrderDialog` setа `currentOrderId` no Context (linha 93 de `OrdersContext.tsx`). O navega-para-`/pdv` precisa acontecer **após sucesso da criação**.

Há duas estratégias possíveis. O agente deve **escolher uma e documentar a razão**:

**Estratégia A — push no callback de sucesso do NewOrderDialog**:
- Componente `NewOrderDialog` chama `createOrder` do Context, aguarda promise resolver, então faz `router.push('/pdv')` e `onClose()`.
- Pró: a navegação é responsabilidade do componente que consumiu a ação, segue padrão React idiomático.
- Contra: precisa replicar essa lógica em qualquer componente futuro que crie comanda.

**Estratégia B — push dentro do createOrder do Context**:
- O método `createOrder` no `OrdersContext` faz o `router.push('/pdv')` direto.
- Pró: única fonte de verdade — toda criação de comanda leva a `/pdv` automaticamente.
- Contra: Context acoplado a router. Alguns considerariam impuro. Em apps Next.js modernos é aceito, mas é convenção.

**Recomendação**: Estratégia B (acoplamento aceitável, código mais resiliente). Mas se o agente preferir A por purismo, ok — desde que documente.

Independente da escolha, o efeito browser observável é o mesmo: criar comanda em qualquer tela leva o usuário pra `/pdv` com a comanda nova já selecionada à direita.

---

## Aceites browser (executar no Chrome de verdade — não inferir)

**Pré-condições**: backend rodando (porta 8080 via `./start-backend-dev.sh`), PDV rodando (porta 3000), Admin opcional. Logar como ADMIN (`admin@dipdv.dev` / `dipdv@2025`). Caixa aberto. Pelo menos 2 comandas criadas previamente.

### Cenário A — Selecionar comanda existente fora de `/pdv`
1. [ ] Navegar para `/manage/products`
2. [ ] Clicar no botão "N comandas abertas" no header → drawer abre
3. [ ] Clicar numa comanda da lista → drawer fecha + URL muda para `/pdv` + comanda escolhida aparece no painel direito (`CurrentOrderPanel`)
4. [ ] Repetir com outra comanda do drawer → painel direito atualiza para a nova escolhida

### Cenário B — Selecionar comanda existente já em `/pdv`
1. [ ] Estar em `/pdv` com uma comanda selecionada (ex: "mesa 1")
2. [ ] Abrir drawer "Comandas abertas" → clicar em outra comanda (ex: "mesa 2")
3. [ ] Drawer fecha, URL continua `/pdv`, painel direito atualiza para "mesa 2"

### Cenário C — Criar comanda nova fora de `/pdv`
1. [ ] Navegar para `/manage/categories`
2. [ ] Clicar no indicador do header → drawer abre
3. [ ] Clicar "+ Nova comanda" no drawer → drawer fecha, modal `NewOrderDialog` abre
4. [ ] Preencher identificador "Mesa Teste 6.2" → submeter
5. [ ] Modal fecha + URL muda para `/pdv` + comanda "Mesa Teste 6.2" aparece selecionada no painel direito

### Cenário D — Criar comanda nova já em `/pdv` (sanity check, não deve quebrar)
1. [ ] Estar em `/pdv`
2. [ ] Clicar "+ Nova comanda" → modal abre
3. [ ] Submeter → modal fecha, comanda nova aparece no painel direito
4. [ ] URL continua `/pdv`, sem loop de refresh

### Cenário E — Regressão: outras navegações do drawer não quebraram
1. [ ] Em qualquer tela, abrir drawer
2. [ ] Clicar no "X" do drawer → drawer fecha, URL não muda
3. [ ] Em qualquer tela, abrir drawer, clicar no overlay escuro (área fora do painel) → drawer fecha, URL não muda

---

## Disciplina obrigatória no relatório final

1. **Output literal** de `cd frontend && npm run build` — verde.
2. **Output literal** de `git status` — working tree limpo após commits.
3. **Output literal** de `git log --oneline fix/order-navigation ^develop` — commits da sprint.
4. **Decisão entre Estratégia A e B** documentada com justificativa.
5. **Aceites browser** marcados `[x]` / `[ ]` / `[FAIL]` por cenário.
6. Se o agente **não conseguiu rodar Chrome**, marcar todos os aceites como `[ ]` e declarar explicitamente que o último mile fica para validação humana — **não inferir** a partir de leitura de código ou curl.

---

## Fora de escopo (NÃO atacar nesta sprint)

- **Persistência de `currentOrderId` em refresh**: hoje o Context usa React state simples. F5 perde a comanda atual. É fragilidade adjacente conhecida, mas não é o sintoma reportado — vai para backlog ou Sprint futura de polimento.
- **Comanda no mobile/tablet retrato**: `pdv/page.tsx` tem `TODO: Implementar drawer de comanda para mobile`. Buraco operacional importante mas separado deste bug.
- **PWA mínimo**: nenhum manifest/service worker no projeto hoje. Sprint à parte.
- **Polimento visual / Claude Design**: sprint à parte, futura.

---

## Commit hygiene

- Branch dedicada `fix/order-navigation`.
- Commits granulares:
  - `fix(orders): redirect to /pdv when selecting order from drawer`
  - `fix(orders): redirect to /pdv after creating new order` (ou similar dependendo da estratégia escolhida)
- Push após build verde + aceites browser executados (pelo dono, se agente não conseguir browser).

---

## Pós-sprint

1. Smoke test pelo dono (~3 minutos cobrindo os 5 cenários).
2. Se passar: merge `fix/order-navigation` → `develop` com `--no-ff`.
3. Atualizar `PROJECT_STATE.md` na seção de polimento consolidado:
   - "Sprint 6.2: navegação de comanda em qualquer tela leva a `/pdv` automaticamente"
4. Próxima sprint: continuar auditoria de fluxo operacional (lista de atritos a ser definida pelo dono).

---

## Resumo executivo

| Bug | Localização | Linhas afetadas | Severidade |
|-----|-------------|------------------|------------|
| Selecionar comanda fora de /pdv não navega | `OpenOrdersDrawer.tsx` linha 19 | ~3 | Operacional, bloqueia fluxo real |
| Criar comanda fora de /pdv não navega | `OrdersContext.tsx` linha 93 OU `NewOrderDialog.tsx` callback | ~3-5 | Operacional, bloqueia fluxo real |

Sprint curtíssima. Sem testes IT novos (mudança puramente de navegação client-side). Aceite browser é o único validador que importa.
