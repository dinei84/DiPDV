# Prompt — Antigravity: Validação Final Sprint 1 (Modifiers)

## Contexto

Sprint 1 quase fechada. Antes de declarar concluída e partir para o módulo
Order, precisamos confirmar 3 itens que não foram explicitados no relatório.

---

## Tarefa 1 — Confirmar PR aberto

Verificar se o PR foi aberto corretamente:

```bash
git status
git log --oneline -3
git branch
```

Esperado: branch `feature/US03.3-modifier-groups` com commits,
PR aberto no GitHub apontando para `develop`.

Se não estiver aberto:
```bash
git push origin feature/US03.3-modifier-groups
# Abrir PR no GitHub: feature/US03.3-modifier-groups → develop
# Título: "feat(catalog): ModifierGroup + ModifierOption — Sprint 1"
```

---

## Tarefa 2 — Confirmar smoke tests via curl

Com a aplicação rodando (`.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`),
executar e colar o output de cada comando:

**Obter token:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001","email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
echo $TOKEN
```

**Criar grupo:**
```bash
curl -s -X POST http://localhost:8080/api/v1/modifier-groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Adicionais","minSelect":0,"maxSelect":3}' | jq .
```
Esperado: 201 com id do grupo.

**Adicionar opção com maxQuantity > 1:**
```bash
GROUP_ID="COLAR_UUID_DO_GRUPO_AQUI"
curl -s -X POST http://localhost:8080/api/v1/modifier-groups/$GROUP_ID/options \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Bacon","priceAddition":3.50,"maxQuantity":3}' | jq .
```
Esperado: 201 com `maxQuantity: 3`.

**Vincular grupo a produto:**
```bash
PRODUCT_ID="COLAR_UUID_DO_PRODUTO_AQUI"
curl -s -X POST \
  "http://localhost:8080/api/v1/products/$PRODUCT_ID/modifiers/$GROUP_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: 200 ou 204.

**Listar modificadores do produto:**
```bash
curl -s "http://localhost:8080/api/v1/products/$PRODUCT_ID/modifiers" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: lista com o grupo e suas opções.

**Produto simples (sem grupos):**
```bash
SIMPLE_ID="COLAR_UUID_DE_PRODUTO_SEM_GRUPOS"
curl -s "http://localhost:8080/api/v1/products/$SIMPLE_ID/modifiers" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: `[]`

**Validação minSelect > maxSelect:**
```bash
curl -s -X POST http://localhost:8080/api/v1/modifier-groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Inválido","minSelect":3,"maxSelect":1}' | jq .
```
Esperado: 400 com mensagem de erro.

---

## Tarefa 3 — Confirmar fetch join (1 query)

Este é o critério de aceitação de performance mais importante da Sprint.

Com `show-sql: true` ativo no `application-dev.yml`, rodar o endpoint
de modificadores do produto e copiar o trecho do log:

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -A 5 "Hibernate:"
```

Ou simplesmente copiar do console as linhas `Hibernate: select ...`
que aparecem ao chamar `GET /products/{id}/modifiers`.

**Esperado — 1 única query com JOIN:**
```sql
Hibernate: select mg1_0.id, mg1_0.name, ... o1_0.modifier_group_id, ...
           from modifier_groups mg1_0
           left join modifier_options o1_0 on ...
           where mg1_0.id in (select ...)
```

**Sinal de problema — N+1 queries:**
```sql
Hibernate: select * from modifier_groups where id=?
Hibernate: select * from modifier_options where modifier_group_id=?
Hibernate: select * from modifier_options where modifier_group_id=?   ← repetindo
```

Se estiver em N+1, reportar antes de prosseguir para o módulo Order.

---

## Checklist

- [ ] PR aberto `feature/US03.3-modifier-groups` → `develop`
- [ ] `POST /modifier-groups` → 201 ✓
- [ ] `POST /modifier-groups/{id}/options` com `maxQuantity: 3` → 201 ✓
- [ ] Vínculo produto ↔ grupo criado → 200/204 ✓
- [ ] `GET /products/{id}/modifiers` → grupos + opções ✓
- [ ] `GET /products/{simpleId}/modifiers` → `[]` ✓
- [ ] `minSelect > maxSelect` → 400 ✓
- [ ] Log SQL confirma 1 query no fetch join ✓

Colar os outputs dos curls e o trecho do log SQL ao reportar.
