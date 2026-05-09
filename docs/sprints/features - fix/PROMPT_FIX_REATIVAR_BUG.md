# Prompt — Bug: botão "Reativar" mostra modal de "Desativar"

Branch: continuar em `feature/tenant-active-toggle`.

---

## Contexto

A validação manual descobriu inconsistência no toggle de
ativação de tenant que você acabou de implementar:

**Reportado pelo usuário:**
- Cenário 1 (desativar tenant ativo) ✅ funcionou
- Cenário 2 (reativar tenant inativo) ✗ FALHA:
  > "quando eu clico para reativar o tenant ele mostra a mesma
  > mensagem de desativação. Quando clico em Desativar, ele
  > ativa novamente o tenant."

Resumindo o sintoma:
- Tenant inativo
- Botão exibido diz **"Reativar tenant"**
- Clicar abre modal cujo texto é **de desativação**
- Confirmar a desativação **ativa** o tenant

Há inconsistência entre **rótulo do botão**, **texto do modal**
e **ação executada**.

---

## Hipóteses iniciais (verificar uma a uma, sem corrigir)

1. **Modal genérico mas texto fixo de desativação.** O componente
   `ConfirmModal` foi feito reusável, mas o texto "Tem certeza
   que deseja desativar..." pode estar hardcoded. Quando o botão
   "Reativar" usa o mesmo modal, exibe o texto errado.

2. **Estado de `active` desatualizado no componente.** Após o
   primeiro PUT (Cenário 1), a página não re-renderizou com o
   novo `active=false`. Componente continua achando que está
   ativo, então:
   - Calcula label "Reativar" baseado em estado novo (correto)
   - Mas o texto do modal lê estado antigo (incorreto)
   - E a função de submit envia `active: !active` baseado em
     estado antigo, gerando inversão

3. **Função de submit hardcoded como `active: false`** em vez
   de inverter o estado atual. Combinado com label dinâmica,
   gera o sintoma descrito.

4. **Outro motivo** que você descobrir investigando.

---

## Workflow obrigatório (igual ao do bug do toggle de módulo)

### Fase 1 — Investigação com instrumentação

1. Adicione log no botão e no handler de submit do modal:

   No arquivo onde está o botão de ativar/desativar
   (provavelmente `admin/src/app/(admin)/tenants/[id]/page.tsx`),
   adicione no `onClick` do botão e no callback de confirmação
   do modal:

   ```ts
   console.log('[Toggle] button click', { tenantId, currentActive: tenant?.active });
   // ... código existente ...
   console.log('[Toggle] submit confirmed', { newActive: ???, modalText: ??? });
   ```

   Adapte os nomes às variáveis reais do componente.

2. **Reporte ao usuário:**
   - Caminho do arquivo modificado
   - Onde adicionou os logs
   - Peça que ele:
     - Recarregue a página
     - Tenha um tenant inativo (ele já tem após o Cenário 1)
     - Clique "Reativar"
     - Abra console
     - Cole os logs aqui

3. **Pare e espere a resposta. Não corrija ainda.**

### Fase 2 — Análise estática paralela

Enquanto espera resposta, investigue passivamente:

1. Como o **rótulo do botão** ("Desativar"/"Reativar") é calculado?
2. Como o **texto do modal** é definido? É prop do `ConfirmModal`
   ou é hardcoded dentro dele?
3. Como o **valor de `active` enviado no PUT** é determinado?
   `!tenant.active`? `false` literal? `newActive` calculado em
   algum lugar?
4. Há **três fontes de verdade independentes** sobre o estado
   atual (rótulo, texto, payload)? Se sim, esse é o problema
   arquitetural.

Reporte com referências `arquivo:linha`.

---

## Princípios

- **Não chute solução sem evidência.** Vimos esse padrão antes;
  análise estática isolada gasta rodadas.
- **Mudança mínima.** Não refatore o componente inteiro.
  Provavelmente é uma prop a mais no `ConfirmModal` ou um
  cálculo a corrigir.
- **Não declarar concluído sem `npm run build` passando.**
- **Não simular validação.** Quando corrigir, devolva ao usuário
  pra revalidar Cenário 2.

---

## Após corrigir

- Remover logs de debug
- `npm run build` — sucesso obrigatório
- Reportar:
  - Causa raiz confirmada
  - Mudança aplicada (arquivo:linha + diff curto)
  - Resultado do build verbatim
  - Pedido pro usuário revalidar Cenário 2 (sem simular)
