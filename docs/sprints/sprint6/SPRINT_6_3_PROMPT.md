# SPRINT 6.3 — Dashboard de comandas abertas com indicador de tempo

> Branch sugerida: `feature/orders-dashboard`, criada a partir de `develop` após merge da Sprint 6.2.
> Frente: polimento operacional. Bloqueador soft para Sprint 7 (Deploy) — não queremos deployar sem essa visão.
> Apenas frontend (presumivelmente). Backend já entrega `createdAt` e `status` por comanda; nenhuma mudança esperada lá.

---

## 🚨 Pré-requisitos ao começar

1. **Confirmar estado de partida** (rodar e colar output literal no relatório):
   ```bash
   git checkout develop && git pull origin develop
   git log --oneline -5
   # Esperado no topo: merge da Sprint 6.2 (fix/order-navigation)

   cd backend && mvn test -Dexclude.integration.tests=""
   # Esperado: 209 verdes (ou mais, se Sprint 6.2 adicionou IT — não esperado)

   cd ../frontend && npm run build
   # Esperado: verde
   ```

2. **Backend rodando**: `./start-backend-dev.sh` na raiz do repo.

3. **Branch nova**:
   ```bash
   git checkout -b feature/orders-dashboard
   ```

---

## Anexar a este prompt
- `PROJECT_STATE.md` da `develop` (referência de padrões consolidados, mesmo sabendo que está desatualizado para Sprints 5.x — a memória útil para esta sprint está nas seções "Decisões de produto", "Roles", "Helpers de preço", "Lições aprendidas")
- Este documento (escopo 6.3)

Recomendação ao agente: **ler antes de codar** o `OpenOrdersDrawer.tsx`, o `OpenOrdersIndicator.tsx`, o `OrdersContext.tsx`, e o `pdv/page.tsx` — toda a lógica de comanda aberta já existe e deve ser reusada, não reescrita.

---

## Contexto e objetivo

### O que existe hoje

- Backend retorna comandas abertas via `GET /api/v1/orders?status=OPEN`. Cada comanda traz `id`, `identifier`, `status`, `total`, `itemCount`, `createdAt`.
- Frontend tem `OpenOrdersIndicator` (botão "N comandas abertas" no header) e `OpenOrdersDrawer` (drawer sobreposto que lista as comandas abertas, com formatação básica de tempo "X min").
- A rota `/` (home) hoje carrega o dashboard com widgets de relatórios — válido para ADMIN/MANAGER. Para CASHIER, hoje provavelmente também cai nessa home (a navegação por role do logo na Sprint 6.1 mexeu apenas no link do logo, não na rota raiz em si — confirmar na investigação).
- Decisão do dono: a "primeira coisa que o operador vê ao entrar" deveria ser **a lista de comandas abertas em tela cheia**, com indicador visual de tempo, não um drawer que fecha.

### O que esta sprint constrói

**Tela de dashboard de comandas abertas (`OrdersDashboard`)**, ocupando a viewport inteira, mostrando todas as comandas abertas do tenant como **cards**, ordenados do mais antigo para o mais recente, com **borda/destaque colorido por tempo de espera**:

- 🟢 **Verde**: 0 a 10 minutos desde `createdAt`
- 🟡 **Amarelo**: 10 a 25 minutos
- 🔴 **Vermelho**: mais de 25 minutos

**Esta tela vira a home `/` para o CASHIER.** ADMIN e MANAGER continuam vendo o dashboard atual de relatórios em `/`.

Clicar num card reusa o fluxo da Sprint 6.2: `setCurrentOrderId(id)` + `router.push('/pdv')` — leva o operador à tela de edição da comanda.

Cards atualizam o tempo automaticamente sem refresh manual (polling + recálculo local).

---

## Decisões já tomadas (não revisitar)

1. **Localização**: home `/` do CASHIER (rota raiz, role-aware). Não criar rota nova dedicada.
2. **Thresholds**: fixos no código (verde <10min, amarelo 10-25min, vermelho >25min), com constantes em arquivo dedicado e comentário sinalizando refatoração futura para configurável por tenant.
3. **Origem do tempo**: `createdAt` da comanda (campo já existente no DTO).
4. **Click no card**: navega para `/pdv` com a comanda selecionada (mesmo padrão da Sprint 6.2).
5. **ADMIN e MANAGER**: continuam com a `/` atual (widgets de relatório). **Não tocar nessa parte** desta sprint.
6. **Logo "DiPDV" no header**: CASHIER continua sendo levado para `/` — que agora, sendo o dashboard novo, faz sentido como "home operacional". Não mudar o comportamento do logo nesta sprint, só o conteúdo de `/` para CASHIER.

---

## Implementação

### Frontend PDV (`frontend/`)

#### 1. Constantes de threshold em arquivo dedicado

Criar `frontend/src/lib/orders/orderTimeThresholds.ts`:

```typescript
/**
 * Limites de tempo (em minutos) para classificação visual de comandas abertas
 * no dashboard de pedidos.
 *
 * 🔧 REFATORAÇÃO FUTURA (não nesta sprint):
 * Estes valores devem virar configuráveis por tenant em uma sprint dedicada.
 * Cada negócio tem ritmo diferente:
 *   - Lanchonete rápida: thresholds menores (5/15min)
 *   - Restaurante à la carte: thresholds maiores (20/45min)
 *   - Bar / balada: pode não fazer sentido nenhum threshold
 *
 * Plano de migração quando isso virar configurável:
 *   1. Backend: adicionar campos `orderWarningMinutes` e `orderCriticalMinutes`
 *      na entidade Tenant (migration V17 ou similar).
 *   2. Endpoint admin: `PATCH /api/v1/admin/tenants/{id}` aceita esses campos.
 *   3. Endpoint público: `GET /api/v1/me/tenant-settings` retorna os valores.
 *   4. Frontend: hook `useTenantOrderThresholds()` busca e cacheia os valores;
 *      `orderTimeThresholds.ts` vira fallback caso o hook ainda esteja carregando.
 */

export const ORDER_TIME_THRESHOLDS = {
  /** Comanda recém-aberta (verde) — até este valor em minutos */
  warningMinutes: 10,
  /** Comanda em alerta (amarelo) — entre warningMinutes e este valor */
  criticalMinutes: 25,
  /** Acima de criticalMinutes → vermelho */
} as const;

export type OrderTimeStatus = 'fresh' | 'warning' | 'critical';

/**
 * Classifica o tempo de uma comanda aberta com base em createdAt.
 * Recebe o createdAt como ISO string (mesmo formato que o backend retorna)
 * e o "now" opcional (útil para teste e para recalcular sem refetch).
 */
export function classifyOrderTime(
  createdAtISO: string,
  now: Date = new Date()
): OrderTimeStatus {
  const created = new Date(createdAtISO);
  const elapsedMinutes = (now.getTime() - created.getTime()) / 1000 / 60;

  if (elapsedMinutes < ORDER_TIME_THRESHOLDS.warningMinutes) return 'fresh';
  if (elapsedMinutes < ORDER_TIME_THRESHOLDS.criticalMinutes) return 'warning';
  return 'critical';
}
```

#### 2. Hook de auto-refresh do tempo

Criar `frontend/src/lib/orders/useOrderTimeRefresh.ts`:

```typescript
/**
 * Força re-render a cada N segundos para recalcular tempo decorrido
 * sem precisar refetchar dados do backend.
 *
 * Default: 30s — suficiente para granularidade de "minutos" sem ser caro.
 */
import { useState, useEffect } from 'react';

export function useOrderTimeRefresh(intervalMs: number = 30_000): Date {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);

  return now;
}
```

#### 3. Polling de novas comandas

O `OrdersContext` provavelmente faz fetch apenas no mount. Pra dashboard, é importante refetchar periodicamente (ex: a cada 60s) para pegar comandas criadas por outros usuários no mesmo tenant.

Opções:
- **Opção A**: adicionar `refresh` automático no `OrdersContext` (afeta toda a aplicação)
- **Opção B**: hook `useOrdersPolling()` específico do dashboard, que chama o `refresh()` existente no Context

Recomendação: **Opção B** — escopo isolado, não afeta outras telas. Criar `frontend/src/lib/orders/useOrdersPolling.ts`:

```typescript
import { useEffect } from 'react';
import { useOrders } from './OrdersContext';

export function useOrdersPolling(intervalMs: number = 60_000): void {
  const { refresh } = useOrders();

  useEffect(() => {
    const id = setInterval(refresh, intervalMs);
    return () => clearInterval(id);
  }, [refresh, intervalMs]);
}
```

⚠️ Atenção: o Context atual pode não expor `refresh` (verificar). Se não expuser, expor sem refatoração maior — adicionar ao `value` do Provider.

#### 4. Componente do card

Criar `frontend/src/components/Orders/OrderCard.tsx`. Card deve mostrar:
- Identificador da comanda (ex: "Mesa 5", ou "Anônimo #abcd" via mesma lógica de `OpenOrdersDrawer.formatIdentifier`)
- Tempo decorrido em formato legível ("23 min", "1h 5min" se passar de 60min)
- Total da comanda (formatado em BRL via Intl, **sem dividir por 100** — vide PROJECT_STATE)
- Contagem de itens (`itemCount`)
- **Borda lateral colorida** (4-6px) ou **destaque de fundo sutil** indicando o status de tempo
- Acessibilidade: o card precisa ter contraste suficiente para ser legível para daltônicos — não confiar APENAS na cor. Adicionar ícone ou label textual ao lado ("Atrasada", "Atenção", "OK") em status vermelho/amarelo.

Cor base para os 3 estados (Tailwind, sem trazer biblioteca nova):
- Fresh: borda `border-l-green-500`, fundo `bg-white`
- Warning: borda `border-l-yellow-500`, fundo `bg-yellow-50`
- Critical: borda `border-l-red-500`, fundo `bg-red-50`, com label textual "Atrasada" em vermelho

O card é um `<button>` (não `<div onClick>`) para acessibilidade — recebe foco por teclado, aria-label descreve a comanda.

#### 5. Página do dashboard

Criar `frontend/src/components/Orders/OrdersDashboard.tsx`:

- Recebe a lista de comandas via `useOrders().openOrders`
- Ordena pelo `createdAt` **ascendente** (mais antigo primeiro → as comandas atrasadas ficam visíveis no topo)
- Renderiza grid responsivo de `OrderCard` (sugestão: `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4`)
- Header com título "Pedidos abertos" + contagem total + breakdown rápido por status (ex: "3 atrasadas · 2 em alerta · 5 ok")
- Botão "+ Nova comanda" canto superior direito → mesmo `NewOrderDialog` já existente
- Estado vazio: "Nenhuma comanda aberta no momento." + botão "+ Criar primeira comanda" (centralizados)
- Chama `useOrdersPolling()` e `useOrderTimeRefresh()` internamente

Click no card → `setCurrentOrderId(order.id) + router.push('/pdv')` (mesmo padrão da Sprint 6.2). Como o `OrdersContext.setCurrentOrderId` na Sprint 6.2 não navega sozinho (só `createOrder` faz), o componente é responsável pela navegação. Se quiser DRY, expor um helper `selectAndNavigate(id)` no Context — opcional, fica a critério do agente.

#### 6. Roteamento de `/` por role

O arquivo `frontend/src/app/(pdv)/page.tsx` (ou onde estiver a rota raiz) precisa decidir o que renderizar com base no role:

- **CASHIER** → renderiza `<OrdersDashboard />`
- **ADMIN / MANAGER** → renderiza o dashboard atual de relatórios (não tocar)

Padrão sugerido (preservando o que já existe):

```tsx
const auth = getAuth();
if (auth.role === 'CASHIER') {
  return <OrdersDashboard />;
}
// resto do código atual da home (widgets de relatório) permanece
```

Cuidado: se a `/` atual usa `<RoleGuard>` permitindo ADMIN/MANAGER apenas, é preciso ajustar pra permitir CASHIER também (e a lógica condicional acima cuida da renderização diferenciada).

---

## Aceites browser (executar no Chrome de verdade — não inferir)

**Pré-condições**: backend rodando, frontend rodando, caixa aberto, pelo menos 4 comandas em diferentes idades (criar manualmente: uma agora, uma de 8 min, uma de 15 min, uma de 30 min — ou ajustar `createdAt` no banco se necessário).

### Cenário A — Dashboard como home do CASHIER
1. [ ] Login como CASHIER → URL é `/` → vê dashboard de pedidos em tela cheia
2. [ ] Vê pelo menos 4 cards de comanda (uma de cada faixa de tempo)
3. [ ] Card mais antigo está visualmente no topo da grid (ordenação ascendente por createdAt)
4. [ ] Breakdown no header confere com a contagem ("X atrasadas · Y em alerta · Z ok")

### Cenário B — Cores e label dos cards
1. [ ] Card de comanda criada há <10min tem borda verde, fundo branco, sem label de alerta
2. [ ] Card de comanda criada há 10-25min tem borda amarela, fundo creme, label "Atenção" (ou similar)
3. [ ] Card de comanda criada há >25min tem borda vermelha, fundo vermelho claro, label "Atrasada"
4. [ ] Tempo no card mostra "X min" e atualiza visualmente em até 30s (deixar a tela aberta e cronometrar)

### Cenário C — Click em card navega pra /pdv
1. [ ] Clicar num card de comanda → URL muda para `/pdv` + comanda selecionada aparece à direita
2. [ ] Comportamento idêntico ao do drawer de comandas abertas (Sprint 6.2)

### Cenário D — Nova comanda funciona do dashboard
1. [ ] Botão "+ Nova comanda" no dashboard abre `NewOrderDialog`
2. [ ] Submeter → URL muda para `/pdv` com comanda nova selecionada (idem Sprint 6.2)

### Cenário E — ADMIN e MANAGER continuam vendo home atual
1. [ ] Login como ADMIN → `/` mostra dashboard de relatórios (widgets, gráficos) — não o dashboard de pedidos
2. [ ] Login como MANAGER → idem

### Cenário F — Polling pega comandas criadas em outra sessão
1. [ ] Manter `/` aberto como CASHIER em uma aba
2. [ ] Em outra aba (ou após uma curl manual), criar uma nova comanda
3. [ ] Em até 60 segundos, a comanda nova aparece no dashboard sem refresh manual

### Cenário G — Estado vazio
1. [ ] Cancelar todas as comandas abertas (ou esperar todas serem fechadas)
2. [ ] Dashboard mostra "Nenhuma comanda aberta no momento" + botão "+ Criar primeira comanda"
3. [ ] Clicar no botão abre o `NewOrderDialog` normalmente

### Cenário H — Acessibilidade básica
1. [ ] Navegar pelos cards usando Tab no teclado → cada card recebe foco visível
2. [ ] Pressionar Enter num card focado → navega para `/pdv` igual ao click

---

## Disciplina obrigatória no relatório final

1. **Output literal** de `cd frontend && npm run build` — verde.
2. **Output literal** de `git status` — working tree limpo.
3. **Output literal** de `git log --oneline feature/orders-dashboard ^develop` — commits da sprint.
4. **Decisões em pontos ambíguos** documentadas:
   - Refresh do Context (Opção A vs B): qual foi e por quê.
   - Helper `selectAndNavigate` no Context: criado ou não.
   - Layout exato do card (qual variante de borda/fundo usou).
5. **Aceites browser** — para cada cenário, `[x]` / `[ ]` / `[FAIL]`. Se o agente não tem Chrome, marcar todos `[ ]` com nota explícita "validação humana pendente" — **não inferir** via leitura de código.

---

## Fora de escopo (NÃO atacar)

- **Thresholds configuráveis por tenant**: comentário no código sinaliza migração futura, mas implementação não. Não criar migration V17, não adicionar campos em Tenant.
- **WebSocket / SSE**: polling é suficiente. Não trazer dependência nova.
- **Notificação sonora ou visual flash quando comanda atinge "atrasada"**: pode ser bom UX mas é scope creep pra esta sprint.
- **Redesign visual / Claude Design**: identidade visual fica para sprint dedicada. Aqui é "funcional e organizado", usando Tailwind padrão.
- **Mobile responsivo do dashboard**: o `TODO: Implementar drawer de comanda para mobile` em `pdv/page.tsx` ainda é dívida; o dashboard deve funcionar em desktop e tablet em paisagem. Mobile vertical pode ser feio mas não pode quebrar — grid colapsa para 1 coluna automaticamente via Tailwind.

---

## Commit hygiene

- Branch dedicada `feature/orders-dashboard`.
- Commits granulares sugeridos:
  - `feat(orders): add time threshold constants and classifier`
  - `feat(orders): add useOrdersPolling and useOrderTimeRefresh hooks`
  - `feat(orders): add OrderCard component with time-based visual status`
  - `feat(orders): add OrdersDashboard as home for CASHIER role`
- Push após build verde + aceites browser (pelo dono, se agente não tem Chrome).

---

## Pós-sprint

1. Smoke test pelo dono (~5 minutos cobrindo os 8 cenários).
2. Se passar: merge `feature/orders-dashboard` → `develop` com `--no-ff`.
3. Atualização do `PROJECT_STATE.md` pendente — será feita em sessão separada (auditoria + reescrita), não nesta sprint.
4. Próximas sprints possíveis:
   - **Sprint 6.4**: outros polimentos de fluxo da lista do dono (a definir)
   - **Sprint 7**: mobile/tablet retrato + PWA básico, ou Deploy Render — sequência a decidir após esta sprint.

---

## Resumo executivo

| Componente | Esforço estimado |
|------------|-------------------|
| `orderTimeThresholds.ts` + `classifyOrderTime` | Baixo |
| `useOrderTimeRefresh`, `useOrdersPolling` | Baixo |
| `OrderCard` | Médio (cores + acessibilidade + tempo formatado) |
| `OrdersDashboard` | Médio (grid + breakdown + estado vazio) |
| Roteamento de `/` por role | Baixo (ajuste em `page.tsx`) |
| Aceites browser (humano) | Baixo (~5 min) |

Sprint pequena, escopo coeso, sem backend. Bloqueador soft para Sprint 7 (Deploy) — depois desta, o produto fica visualmente "pronto pra cliente real abrir".
