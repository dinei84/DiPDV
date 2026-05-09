# Prompt — Login do PDV não dispara submit

Branch: continuar em `feature/pdv-modules-consumer`.

---

## Contexto

A validação manual do usuário descobriu bug crítico no login do PDV:

- Acessar `http://localhost:3000/login`
- Preencher credenciais válidas (`admin@dipdv.dev` / `dipdv@2025`)
- Clicar "Entrar"
- **Resultado:** nada acontece. Zero request no Network. Zero
  erro no Console. Zero feedback visual.

Sintoma idêntico ao bug do toggle de módulo no admin que
investigamos sprints atrás: o handler do clique não está sendo
executado.

---

## Workflow obrigatório (igual ao do bug do toggle)

**Não chute hipóteses sobre o que está errado. Instrumente.**

### Fase 1 — Investigação com instrumentação

1. **Adicione log no início do handler de submit do login.**

   Provável caminho: `frontend/src/app/login/page.tsx` ou
   `frontend/src/app/(auth)/login/page.tsx`. Confirme com `find`
   ou listagem.

   No início da função que trata o submit, adicione:
   ```ts
   console.log('[LoginSubmit] called', { email, password: '***' });
   ```

   No início, antes de qualquer `if`, `try`, ou `await`.

2. **Reporte ao usuário:**
   - Caminho exato do arquivo do login
   - Onde adicionou o log (linha aproximada)
   - **Peça ao usuário que recarregue, clique Entrar e cole o
     console aqui.** Você não roda o app — quem roda é o usuário.

3. **Pare e espere a resposta antes de qualquer correção.**

### Fase 2 — Análise estática paralela

Enquanto espera, investigue passivamente as causas possíveis e
reporte hipóteses **sem corrigir**:

1. **O botão tem `type="submit"`?** Procure `<button` no arquivo
   do login e confirme. Botões sem `type` em `<form>` ainda
   submetem por padrão, mas botões fora de `<form>` não.

2. **Há `<form>` envolvendo botão e inputs?** Confirme estrutura.

3. **`onSubmit` está atribuído ao `<form>` ou `onClick` ao
   `<button>`?** Reporte qual padrão foi usado.

4. **`e.preventDefault()` está no início do handler?** Sem isso,
   o form pode estar fazendo redirect padrão de HTML antes do
   código React rodar.

5. **Há algum estado tipo `isLoading` ou `isPending` que pode
   estar travado em `true` desde o mount?** Bloquearia o submit.

6. **Build atual compila?** Rode `npm run build` no `frontend/`
   e cole resultado. Se houver warning de form não controlado ou
   erro silencioso, reporte.

Reporte achados com **arquivo:linha** específicos.

---

## Correção (Fase 3, só após resposta do usuário)

Com base no console do usuário + análise estática:

- **Se log NÃO aparecer no console:** handler não conectado.
  Causa típica: form sem `onSubmit`, ou botão sem `type="submit"`,
  ou button fora de form. Conecte handler corretamente.

- **Se log aparecer mas nada mais:** handler dispara, trava antes
  de `apiFetch`. Causa típica: `e.preventDefault()` ausente
  causando redirect HTML, ou erro síncrono. Adicione mais logs
  até localizar onde para.

- **Se log aparecer e depois `apiFetch` lança erro:** trate o
  erro corretamente.

Em qualquer caso, **mudança mínima**. Não refatore o componente
inteiro.

---

## Após corrigir

1. Remova os logs de debug.
2. `npm run build` — sucesso obrigatório, cole resultado.
3. **Não declare validado.** Devolva ao usuário com:
   - "Implementação corrigida, build OK"
   - Lista dos cenários para o usuário re-validar
   - Sem "Resultados Observados", sem simulação

---

## Princípio (lembrete)

Vimos esse padrão antes — clique sem efeito, sem erro, sem
request. A causa nunca é "lógica errada no handler"; é sempre
"handler nem está sendo invocado". Análise estática isolada
desperdiça rodadas. Logs no browser revelam a verdade em uma
tentativa.
