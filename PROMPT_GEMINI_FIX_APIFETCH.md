# Prompt — Correção: apiFetch quebra em response sem JSON

Branch: continuar em `fix/admin-visual-bugs`.

---

## Causa raiz confirmada (com evidência)

Logs do browser (validados manualmente pelo usuário):

```
[CLICK] {code: 'IFOOD_INTEGRATION', tier: 'PAID', isDisabled: false, ...}
[ModuleToggle] Clicked
[ModuleToggle] Updating state
[ModuleToggle] Sending request {url: '/api/v1/admin/.../enable', ...}
❌ Request failed SyntaxError: Failed to execute 'json' on 'Response':
   Unexpected end of JSON input
   at apiFetch (api.ts:54:14)
[ModuleToggle] Pending finished
```

O handler do toggle **roda corretamente**. A request **é enviada**.
O bug está em `admin/src/lib/api.ts` linha 54: `apiFetch` chama
`response.json()` em qualquer status 2xx, mas os endpoints
`POST /api/v1/admin/modules/tenants/{id}/enable` e `/disable`
retornam **204 No Content** (sem body). `JSON.parse('')` explode.

Sua hipótese anterior (lógica de `action` em `ModuleToggle`) estava
errada. Não modifique `ModuleToggle.tsx` — ele está correto.

---

## Correção

### `admin/src/lib/api.ts`

Tornar `apiFetch` resiliente a respostas sem body:

- Se `response.status === 204` → retornar `null`
- Se header `Content-Length` for `0` → retornar `null`
- Caso contrário, ler como texto e fazer `JSON.parse` apenas se
  o texto não for vazio:

```ts
const text = await response.text();
return text ? JSON.parse(text) : null;
```

A mesma lógica deve valer **antes** de qualquer tentativa de
ler body em ramos de erro (4xx/5xx). Hoje você lê body do erro
para construir `ApiError` — também aplicar o guard:

```ts
const text = await response.text();
const body = text ? safeParseJson(text) : null;
throw new ApiError(response.status, body, body?.message ?? `HTTP ${response.status}`);
```

`safeParseJson` é helper local que retorna `null` em vez de
lançar.

### `ModuleToggle.tsx`

**Não tocar.** Está correto. Apenas remover os logs de debug
adicionados durante a investigação (linhas 31-82 — só os
`console.log`, não a lógica).

---

## Por que esse bug provavelmente afeta outros pontos

Endpoints administrativos costumam retornar 204 em operações de
write sem retorno. Verifique:

- `POST /api/v1/admin/modules/tenants/{id}/enable` — 204
- `POST /api/v1/admin/modules/tenants/{id}/disable` — 204
- `PUT /api/v1/admin/tenants/{id}` — confirmar (pode ser 200 com
  body ou 204)

Se o PUT também é 204, o "salvar nome" do tenant também está
quebrado pelo mesmo motivo (talvez sem o usuário ter percebido —
salva certo no backend mas mostra erro). A correção em `apiFetch`
resolve todos de uma vez.

---

## Validação após o fix

1. Aplicar correção em `api.ts`.
2. Remover logs de debug de `ModuleToggle.tsx`.
3. Subir o admin e testar manualmente:
   - Toggle PAID liga e desliga sem erro no console.
   - Network tab mostra POST 204 com response body vazio.
   - UI atualiza otimisticamente, persiste após F5.
   - Salvar nome do tenant (PUT) também funciona.
4. Reportar com:
   - Diff de `api.ts` (texto, não imagem).
   - Confirmação que `ModuleToggle.tsx` voltou ao original (sem
     logs).
   - Status code observado em cada endpoint testado (toggle, PUT).

---

## Princípio (lembrete)

A lição desta investigação: análise estática sem rodar o código
não basta para diagnosticar bugs de runtime. Quando o usuário
reportar "nada acontece" em ação interativa, **sempre** peça
instrumentação real (logs no browser) antes de propor correção.
Sua proposta anterior (`isOptimistic` desatualizado) parecia
plausível mas estava errada — só os logs revelaram que o bug
era em `apiFetch`.
