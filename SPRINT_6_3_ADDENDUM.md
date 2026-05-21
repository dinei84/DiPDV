# SPRINT 6.3 — Adendo de escopo (mesma branch, novos commits)

> Branch: continuação de `feature/orders-dashboard` (não criar branch nova).
> Esta extensão **não** revisa o trabalho já feito na 6.3 — ele está correto.
> Corrige regressão exposta pela validação browser + faz uma melhoria estrutural de feature gating.

---

## Contexto da regressão

Durante o smoke test browser da Sprint 6.3 (entregue verde por build mas sem aceite browser), descobrimos:

- O `page.tsx` da raiz `/` retorna `null` para roles que não sejam CASHIER, com o comentário "ADMIN e MANAGER vêem o DashboardWidget renderizado no layout".
- Esse comentário é **factualmente verdadeiro**: o `layout.tsx` em `(pdv)` renderiza `<DashboardWidget />` no `<main>` para roles ADMIN/MANAGER.
- **Porém**, o `DashboardWidget` está envolto em `<ModuleGate module="REPORTS">` (`layout.tsx` linha ~107).
- O tenant de desenvolvimento (`Lanchonete Dev`, `00000000-0000-0000-0000-000000000001`) **não tem nenhum módulo ativo** — confirmado via banco:
  ```
  id: 00000000-0000-0000-0000-000000000001
  name: Lanchonete Dev
  modules: {NULL}
  ```
- Resultado: ModuleGate bloqueia o widget → `page.tsx` retorna null → tela em branco para ADMIN/MANAGER.

Antes da Sprint 6.3, o `page.tsx` retornava um **placeholder textual** ("Módulo de pedidos em desenvolvimento."), que mascarava o problema do widget invisível. A Sprint 6.3 removeu o placeholder, expondo a regressão latente.

**Implicação maior**: este bug NÃO é específico do tenant de dev. Qualquer cliente real onboarded sem REPORTS habilitado pelo SUPER_ADMIN cai na mesma tela branca. É um bug arquitetural, não um detalhe de seed.

---

## Decisão de produto (tomada, não revisitar)

A home `/` passa a renderizar `<OrdersDashboard />` **para TODOS os roles autenticados** (ADMIN, MANAGER, CASHIER). Razões:

1. **Resolve o bug atual** e **previne o bug latente** de qualquer tenant sem REPORTS.
2. **Coerente com o perfil de cliente** (dono de microempresa que também opera o PDV — quem é ADMIN também precisa ver comandas abertas em tempo real, não apenas KPIs).
3. **Reuso máximo** do `OrdersDashboard` que acabou de ser construído.
4. **`DashboardWidget` migra para `/reports`** (onde a proteção por REPORTS já é natural, a rota inteira já é gated).

O resultado conceitual:
- **`/`** (home, todos os roles): dashboard operacional de comandas abertas com indicador de tempo.
- **`/reports`** (ADMIN/MANAGER + REPORTS habilitado): `DashboardWidget` no topo (KPIs do dia) + filtros e relatórios detalhados embaixo.
- **`/pdv`**: tela de edição de comanda específica (não muda).

---

## Mudanças concretas

### 1. Simplificar `frontend/src/app/(pdv)/page.tsx`

**Antes** (estado atual da branch):
```tsx
'use client';
import { getAuth } from '@/lib/auth';
import OrdersDashboard from '@/components/Orders/OrdersDashboard';

export default function HomePage() {
  const auth = getAuth();
  if (auth?.role === 'CASHIER') {
    return <OrdersDashboard />;
  }
  return null;
}
```

**Depois**:
```tsx
'use client';
import OrdersDashboard from '@/components/Orders/OrdersDashboard';

export default function HomePage() {
  return <OrdersDashboard />;
}
```

Não precisa de role check porque o `AuthGuard` do layout já garante usuário autenticado, e o `OrdersDashboard` é apropriado para qualquer role autenticado.

### 2. Remover `DashboardWidget` do layout

Em `frontend/src/app/(pdv)/layout.tsx`, na seção `<main>`, remover:

```tsx
{(isAdmin || isManager) && (
  <ModuleGate module="REPORTS">
    <DashboardWidget />
  </ModuleGate>
)}
{children}
```

Deixar apenas:

```tsx
{children}
```

Remover também o `import DashboardWidget from '@/components/dashboard/DashboardWidget';` no topo do arquivo, **se não for mais usado em outro lugar do layout**. Verificar antes de remover.

### 3. Adicionar `DashboardWidget` ao topo de `/reports`

Em `frontend/src/app/(pdv)/reports/page.tsx`, adicionar `<DashboardWidget />` como primeiro elemento do conteúdo da página (antes dos filtros / tabela de top products / etc).

O componente já tem proteção interna por role (`canViewReports`), e a rota `/reports` já está atrás de `<RoleGuard roles={['ADMIN', 'MANAGER']}>` + ModuleGate REPORTS. Camadas de proteção alinhadas.

### 4. Verificar imports órfãos

Após as mudanças, garantir que `layout.tsx` não tem imports não usados:
- `DashboardWidget` — provavelmente não é mais necessário
- `ModuleGate` — pode ainda ser usado em outros lugares do layout (ex: link de Relatórios), confirmar

---

## Aceites browser (executar no Chrome de verdade)

### Cenário A — Home unificada para ADMIN
1. [ ] Login como `admin@dipdv.dev` → URL é `/` → vê `OrdersDashboard` (mesma tela do CASHIER)
2. [ ] Cards de comanda aparecem, breakdown de status no header, botão "+ Nova comanda" funciona

### Cenário B — Home unificada para MANAGER
1. [ ] Login como qualquer MANAGER → `/` mostra `OrdersDashboard` igual ao ADMIN

### Cenário C — Home unificada para CASHIER (regressão da 6.3 original)
1. [ ] Login como CASHIER → `/` continua mostrando `OrdersDashboard` (comportamento da 6.3 não regrediu)

### Cenário D — DashboardWidget agora em /reports
1. [ ] Como ADMIN, navegar para `/reports` → no topo da página aparece o `DashboardWidget` com KPIs do dia (se REPORTS habilitado)
2. [ ] Se REPORTS não habilitado: `/reports` exibe `ModuleNotAvailable` (comportamento de antes, sem regressão)
3. [ ] Filtros e tabela de top products continuam funcionando abaixo do widget

### Cenário E — Layout limpo
1. [ ] Em todas as rotas (`/`, `/manage/products`, `/pdv`, etc.) o header continua com `OpenOrdersIndicator` e `CashRegisterIndicator`
2. [ ] Nenhuma tela mostra DashboardWidget renderizado duas vezes
3. [ ] Console do browser sem erros novos

### Cenário F — Tenant sem nenhum módulo (cobre o bug original)
1. [ ] Confirmar via banco que o tenant `Lanchonete Dev` continua com `modules: {NULL}` (não habilitar nada)
2. [ ] Login como ADMIN desse tenant → `/` mostra dashboard de comandas, NÃO tela branca
3. [ ] `/reports` mostra `ModuleNotAvailable` (esperado, REPORTS não habilitado)

---

## Disciplina obrigatória no relatório (continuação da 6.3)

1. **Output literal** de `cd frontend && npm run build` — verde.
2. **Output literal** de `git status` — limpo.
3. **Output literal** de `git log --oneline feature/orders-dashboard ^develop` — agora deve mostrar os 4 commits da 6.3 original + os novos commits desta extensão.
4. **Aceites browser** — todos `[ ]` se o agente não tem Chrome, com nota explícita. Validação humana pendente.

---

## Commits sugeridos (extensão)

- `fix(home): unify / for all roles to show OrdersDashboard`
- `refactor(reports): move DashboardWidget from layout to /reports page`

Manter os 4 commits originais da 6.3. Não fazer rebase nem squash — preservar histórico.

---

## Fora de escopo

- Adicionar telemetria de "quantos clientes têm REPORTS habilitado vs não" — interessante, mas não é o problema agora.
- Mudar o ModuleGate para mostrar fallback amigável em vez de bloquear silenciosamente — escopo de polimento separado.
- Criar uma rota dedicada "/dashboard" para ADMIN/MANAGER com layout específico — over-engineering pro estágio atual.

---

## Pós-extensão

1. Smoke test pelo dono dos 6 cenários acima + os 8 cenários originais da 6.3 (~10 min total).
2. Se passar: merge `feature/orders-dashboard` → `develop` com `--no-ff`. Mensagem cobre 6.3 inteira.
3. Adicionar ao `PROJECT_STATE.md` (na próxima sessão de auditoria):
   - **Lição aprendida**: "ModuleGate silencioso + placeholder removido = regressão invisível. Quando um placeholder é removido, sempre verificar se o conteúdo real depende de feature flags que podem não estar habilitadas em todos os tenants."
   - **Lição aprendida 2**: "Sempre validar comportamento em tenant sem nenhum módulo ativo — é o pior caso e o mais provável em onboarding de cliente novo."
   - **Decisão de produto**: "Home `/` é única para todos os roles, sempre `OrdersDashboard`. KPIs do dia ficam consolidados em `/reports`."

---

## Resumo executivo

| Mudança | Linhas afetadas | Risco |
|---------|-----------------|-------|
| Simplificar `page.tsx` da raiz | ~10 | Baixo |
| Remover DashboardWidget do layout | ~5 | Baixo |
| Adicionar DashboardWidget ao /reports | ~3 | Baixo |
| Verificar imports órfãos | ~2 | Trivial |

Mudança pequena, alta clareza arquitetural, resolve bug confirmado + previne bug latente em qualquer tenant novo.
