# Prompt — Antigravity: Validação Final Sprint 2

---

## Contexto

Build SUCCESS com 53 testes unitários. Docker estava desligado durante
a implementação. Este prompt executa os smoke tests com o banco rodando
e fecha o Sprint 2.

---

## Tarefa 1 — Subir o ambiente

```bash
# Na raiz do repositório
docker compose up -d

# Aguardar o postgres ficar healthy
docker compose ps

# Subir a aplicação
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Verificar no console:
- Flyway executa V6 sem erros
- `Started DiPdvApplication` aparece sem exceções
- `http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

## Tarefa 2 — Smoke tests completos

Obter token:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
```

**[1] Abrir caixa → esperado: 201, `status: "OPEN"`**
```bash
curl -s -X POST http://localhost:8080/api/v1/cash-registers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"openingBalance": 100.00}' | jq .
```

**[2] Sangria → esperado: 201**
```bash
REGISTER_ID="UUID_DO_CAIXA"
curl -s -X POST \
  "http://localhost:8080/api/v1/cash-registers/$REGISTER_ID/movements" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"type":"BLEEDING","amount":20.00,"description":"Retirada para troco"}' | jq .
```

**[3] Criar pedido, adicionar item e fechar**
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}' | jq -r .id)

PRODUCT_ID="UUID_DO_XBURGUER"
curl -s -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":"'$PRODUCT_ID'","quantity":1,"modifiers":[]}' | jq .totalPrice

curl -s -X PATCH "http://localhost:8080/api/v1/orders/$ORDER_ID/close" \
  -H "Authorization: Bearer $TOKEN" | jq .status
```
Esperado: `"CLOSED"`

**[4] Pagamento CASH → esperado: `status: "PAID"`, `changeAmount >= 0`**
```bash
IDEMPOTENCY_KEY=$(powershell -Command "[guid]::NewGuid().ToString()")
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId":"'$ORDER_ID'",
    "method":"CASH",
    "amount":20.00,
    "idempotencyKey":"'$IDEMPOTENCY_KEY'"
  }' | jq .
```

**[5] Idempotência → esperado: mesmo `id` do payment anterior**
```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId":"'$ORDER_ID'",
    "method":"CASH",
    "amount":20.00,
    "idempotencyKey":"'$IDEMPOTENCY_KEY'"
  }' | jq .id
```
Colar os dois IDs lado a lado para confirmar que são iguais.

**[6] NFC-e automática → esperado: `status: "ISSUED"`, `accessKey` com 44 dígitos**
```bash
curl -s "http://localhost:8080/api/v1/nfce/order/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Confirmar: `accessKey | length == 44`

**[7] Fechar caixa → esperado: `status: "CLOSED"`, `difference` calculado**
```bash
curl -s -X PATCH \
  "http://localhost:8080/api/v1/cash-registers/$REGISTER_ID/close" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"physicalBalance": 95.00}' | jq '{status, closingBalance, physicalBalance, difference}'
```

**[8] Audit log → esperado: linha `CASH_REGISTER_CLOSED`**
```bash
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT action, entity, created_at FROM audit_log ORDER BY created_at DESC LIMIT 5;"
```

**[9] Pagamento com caixa fechado → esperado: 409**
```bash
NEW_KEY=$(powershell -Command "[guid]::NewGuid().ToString()")
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId":"'$ORDER_ID'",
    "method":"PIX",
    "amount":5.00,
    "idempotencyKey":"'$NEW_KEY'"
  }' | jq .
```

---

## Tarefa 3 — Corrigir DiPdvApplicationTests

O teste de contexto Spring estava falhando por banco indisponível.
Com o Docker rodando, executar:

```bash
.\mvnw.cmd test
```

Esperado: **54 testes passando** (53 unitários + 1 contextLoads).

Se ainda falhar, verificar se existe `application-test.yml` com:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_dev
    username: dipdv_app
    password: dipdv_local_2025
  flyway:
    enabled: true
```

---

## Tarefa 4 — Commit e PR

```bash
git add .
git commit -m "feat(payment): smoke tests validados — Sprint 2 completo

- 54 testes passando (unitários + contextLoads)
- CashRegister, Payment e NFC-e validados em runtime
- Idempotência confirmada com evidência de IDs idênticos
- audit_log com CASH_REGISTER_CLOSED confirmado

Closes #XX (US02.1, US02.2, US02.3, US02.4)"

git push origin feature/US02.1-cashregister-payment-nfce
```

Abrir PR: `feature/US02.1-cashregister-payment-nfce` → `develop`

---

## Checklist

- [ ] Docker rodando + Flyway V6 executada
- [ ] `actuator/health` → UP
- [ ] [1] Caixa aberto → 201 OPEN
- [ ] [2] Sangria → 201
- [ ] [3] Pedido fechado → CLOSED
- [ ] [4] Pagamento CASH → PAID + changeAmount
- [ ] [5] Idempotência → IDs idênticos (colar os dois)
- [ ] [6] NFC-e → ISSUED + accessKey 44 dígitos
- [ ] [7] Fechar caixa → CLOSED + difference
- [ ] [8] audit_log → CASH_REGISTER_CLOSED
- [ ] [9] Pagamento sem caixa → 409
- [ ] `.\mvnw.cmd test` → 54 testes, BUILD SUCCESS
- [ ] PR aberto com link

Colar outputs de cada curl no relatório.
