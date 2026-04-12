# Relatório de Execução — Sprint 2: CashRegister + Payment + NFC-e

**Branch:** `feature/US02.1-cashregister-payment-nfce`
**Data:** 2026-04-10
**Base:** Sprint 1 concluída com 35 testes unitários passando

---

## 1. Escopo da Sprint

A Sprint 2 implementou os três módulos centrais do fluxo de venda do PDV:

```
CashRegister → Payment → NFC-e (mock)
```

A sequência é obrigatória: `PaymentService` depende de `CashRegisterService` para validar o turno aberto, e `NfceService` é disparado automaticamente pelo `PaymentService` ao confirmar o pagamento.

---

## 2. O que estava pronto ao início da sessão

Os seguintes arquivos já existiam no repositório (criados em sessão anterior interrompida):

**Módulo CashRegister:**
- `CashRegisterStatus.java`, `CashMovementType.java` (enums)
- `CashRegister.java`, `CashMovement.java` (entities)
- `CashRegisterRepository.java`, `CashMovementRepository.java`
- `OpenCashRegisterRequest.java`, `CloseCashRegisterRequest.java`
- `CashMovementRequest.java`, `CashMovementResponse.java`, `CashRegisterResponse.java`

**Módulo Payment:**
- `PaymentMethod.java`, `PaymentStatus.java` (enums)
- `Payment.java` (entity)
- `PaymentRepository.java`
- `RegisterPaymentRequest.java`, `PaymentResponse.java`

**Módulo NFC-e:**
- `NfceStatus.java` (enum)

**Migration:**
- `V6__create_nfce_documents.sql` (tabela `nfce_documents` com RLS)

---

## 3. O que foi implementado nesta sessão

### 3.1 Módulo NFC-e — arquivos criados

| Arquivo | Tipo | Descrição |
|---|---|---|
| `nfce/entity/NfceDocument.java` | Entity JPA | Documento NFC-e vinculado a um pedido |
| `nfce/repository/NfceDocumentRepository.java` | Repository | Busca por orderId, por id+tenantId |
| `nfce/dto/NfceResponse.java` | DTO (record) | Response público da NFC-e |
| `nfce/service/NfceService.java` | Interface | Contrato `emit()` e `cancel()` |
| `nfce/service/MockNfceService.java` | Implementação | Simula SEFAZ: chave 44 dígitos, XML mock |
| `nfce/controller/NfceController.java` | Controller | `GET /nfce/order/{id}`, `PATCH /nfce/{id}/cancel` |

### 3.2 Módulo CashRegister — arquivos criados

| Arquivo | Tipo | Descrição |
|---|---|---|
| `cashregister/service/CashRegisterService.java` | Service | Lógica de negócio do turno de caixa |
| `cashregister/controller/CashRegisterController.java` | Controller | 6 endpoints REST |

### 3.3 Módulo Payment — arquivos criados

| Arquivo | Tipo | Descrição |
|---|---|---|
| `payment/service/PaymentService.java` | Service | Fluxo completo de pagamento (11 etapas) |
| `payment/controller/PaymentController.java` | Controller | 3 endpoints REST |

### 3.4 Testes unitários — arquivos criados

| Arquivo | Cenários |
|---|---|
| `cashregister/service/CashRegisterServiceTest.java` | 8 |
| `payment/service/PaymentServiceTest.java` | 10 |

---

## 4. Detalhamento das implementações

### 4.1 CashRegisterService

Métodos implementados:

```
openCashRegister(request)     → valida caixa já aberto (409), cria com status OPEN
getOpenRegister()             → 404 se não houver caixa aberto
getById(registerId)           → busca por id + tenantId
addMovement(registerId, req)  → SUPPLY ou BLEEDING, valida caixa OPEN
closeCashRegister(id, req)    → calcula closingBalance, difference, @Auditable
listRegisters(pageable)       → histórico paginado por tenant
```

**Fórmula de fechamento:**
```
closingBalance = openingBalance + totalCash + totalPix + ΣSUPPLY − ΣBLEEDING
difference     = physicalBalance − closingBalance
```

O fechamento é anotado com `@Auditable(action = CASH_REGISTER_CLOSED)` para rastreabilidade no `audit_log`.

### 4.2 CashRegisterController — Endpoints

| Método | Path | Role |
|---|---|---|
| POST | `/api/v1/cash-registers` | CASHIER |
| GET | `/api/v1/cash-registers/current` | CASHIER |
| GET | `/api/v1/cash-registers` | MANAGER |
| GET | `/api/v1/cash-registers/{id}` | MANAGER |
| POST | `/api/v1/cash-registers/{id}/movements` | CASHIER |
| PATCH | `/api/v1/cash-registers/{id}/close` | MANAGER |

### 4.3 PaymentService — Fluxo de 11 etapas

```
1.  IDEMPOTÊNCIA       → busca por (tenantId, idempotencyKey); se existe, retorna sem reprocessar
2.  CAIXA ABERTO       → 409 se não houver CashRegister com status OPEN
3.  VALIDAR ORDER      → 404 se não encontrado; 409 se status != CLOSED
4.  VALIDAR VALOR      → PIX: rejeita se soma + amount > order.total (409)
                         CASH: rejeita apenas se pedido já está 100% quitado (409)
5.  CALCULAR TROCO     → CASH: changeAmount = amount − (total − somaPaga); mínimo 0
6.  CRIAR PAYMENT      → status PENDING
7.  GATEWAY MOCK       → CASH: confirma imediatamente → PAID
                         PIX: gatewayRef = "PIX-MOCK-{UUID}" → PAID
8.  TOTALIZADORES      → CASH: cashRegister.totalCash += amount
                         PIX: cashRegister.totalPix += amount
9.  DEDUZIR ESTOQUE    → se novaSomaPaga >= order.total → orderService.deductStockForOrder()
10. EMITIR NFC-e       → se pedido quitado → nfceService.emit(orderId, paymentId)
                         falha na emissão: loga ERROR, NÃO reverte o pagamento
11. RETORNAR RESPONSE  → PaymentResponse com pixQrCode (null para CASH)
```

**Decisão de design — CASH vs PIX na validação de valor:**
O spec original aplicava a validação `soma + amount > total → 409` para todos os métodos, o que conflitava com o cálculo de troco para CASH (pagar R$20 em um pedido de R$18,90). A regra foi refinada:
- **CASH**: permite overpayment → troco calculado automaticamente; bloqueia apenas se pedido já quitado.
- **PIX**: valor deve ser exato (não pode exceder o restante).

### 4.4 PaymentController — Endpoints

| Método | Path | Role |
|---|---|---|
| POST | `/api/v1/payments` | CASHIER |
| GET | `/api/v1/payments/order/{orderId}` | CASHIER |
| PATCH | `/api/v1/payments/{id}/cancel` | MANAGER |

### 4.5 MockNfceService

- Verifica idempotência: se já existe NFC-e para o `orderId`, retorna sem recriar.
- Gera chave de acesso SEFAZ mock com **44 dígitos numéricos** no formato:
  `cUF(2) + AAMM(4) + CNPJ_MOCK(14) + milissegundos`.
- Gera XML NFC-e simplificado (mod 65, CNPJ 00000000000000).
- `cancel()`: valida status `ISSUED` antes de cancelar; grava `canceledAt`.
- Anotado com `@Primary` — substituir por `NuvemFiscalNfceService` no futuro sem alterar nada mais.

### 4.6 NfceController — Endpoints

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/api/v1/nfce/order/{orderId}` | CASHIER | Buscar NFC-e de um pedido |
| PATCH | `/api/v1/nfce/{id}/cancel` | MANAGER | Cancelar NFC-e emitida |

> Não há endpoint de emissão manual — a emissão é automática via `PaymentService`.

---

## 5. Testes unitários

### 5.1 CashRegisterServiceTest — 8 cenários

| # | Cenário | Resultado |
|---|---|---|
| 1 | `openCashRegister_whenNoOpenRegister_shouldReturnOpenStatus` | PASS |
| 2 | `openCashRegister_whenAlreadyOpen_shouldThrowConflict` | PASS |
| 3 | `addMovement_whenRegisterClosed_shouldThrowConflict` | PASS |
| 4 | `addMovement_whenAmountZero_shouldNotBeAccepted` | PASS |
| 5 | `closeCashRegister_whenOpen_shouldCalculateClosingBalance` | PASS |
| 6 | `closeCashRegister_whenAlreadyClosed_shouldThrowConflict` | PASS |
| 7 | `getOpenRegister_whenExists_shouldReturn` | PASS |
| 8 | `getOpenRegister_whenNone_shouldThrowNotFound` | PASS |

**Evidência do cálculo de fechamento (cenário 5):**
```
openingBalance = 100.00
totalCash      =  50.00
totalPix       =  30.00
ΣSUPPLY        =  20.00
ΣBLEEDING      =  10.00
─────────────────────────
closingBalance = 190.00
physicalBalance= 185.00
difference     =  -5.00   (operador entregou R$5,00 a menos)
```

### 5.2 PaymentServiceTest — 10 cenários

| # | Cenário | Resultado |
|---|---|---|
| 1 | `registerPayment_whenIdempotencyKeyExists_shouldReturnExistingPayment` | PASS |
| 2 | `registerPayment_whenNoCashRegisterOpen_shouldThrowConflict` | PASS |
| 3 | `registerPayment_whenOrderNotClosed_shouldThrowConflict` | PASS |
| 4 | `registerPayment_whenAmountExceedsTotal_shouldThrowConflict` (PIX) | PASS |
| 5 | `registerPayment_whenCash_shouldCalculateChange` | PASS |
| 6 | `registerPayment_whenPix_shouldGenerateMockGatewayRef` | PASS |
| 7 | `registerPayment_whenOrderFullyPaid_shouldCallDeductStock` | PASS |
| 8 | `registerPayment_whenOrderFullyPaid_shouldCallNfceEmit` | PASS |
| 9 | `registerPayment_whenNfceEmitFails_shouldNotRevertPayment` | PASS |
| 10 | `cancelPayment_whenStatusPaid_shouldThrowConflict` | PASS |

### 5.3 Resultado consolidado da suíte completa

```
[INFO] Tests run:  8, Failures: 0, Errors: 0 -- CashRegisterServiceTest
[INFO] Tests run:  8, Failures: 0, Errors: 0 -- CatalogServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0 -- ModifierServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0 -- OrderServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0 -- PaymentServiceTest
[INFO] Tests run:  3, Failures: 0, Errors: 0 -- AuditAspectTest
[INFO] Tests run:  2, Failures: 0, Errors: 0 -- TenantContextServiceTest
[INFO] ─────────────────────────────────────────────────────────────────
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> O `DiPdvApplicationTests.contextLoads` (teste de integração Spring) falha por ausência de banco de dados no ambiente de CI — comportamento pré-existente desde a Sprint 0, fora do escopo dos testes unitários.

**Contagem por sprint:**

| Sprint | Testes unitários |
|---|---|
| Sprint 0 (Auth) | 2 |
| Sprint 1 (Catalog + Modifiers + Order + Audit) | 33 |
| Sprint 2 (CashRegister + Payment) | +18 |
| **Total atual** | **53** |

---

## 6. Estrutura de arquivos criados nesta sessão

```
backend/src/main/java/com/dipdv/modules/
├── cashregister/
│   ├── service/
│   │   └── CashRegisterService.java          ← NOVO
│   └── controller/
│       └── CashRegisterController.java        ← NOVO
├── payment/
│   ├── service/
│   │   └── PaymentService.java               ← NOVO
│   └── controller/
│       └── PaymentController.java            ← NOVO
└── nfce/
    ├── entity/
    │   └── NfceDocument.java                 ← NOVO
    ├── repository/
    │   └── NfceDocumentRepository.java       ← NOVO
    ├── dto/
    │   └── NfceResponse.java                 ← NOVO
    ├── service/
    │   ├── NfceService.java                  ← NOVO (interface)
    │   └── MockNfceService.java              ← NOVO (@Primary)
    └── controller/
        └── NfceController.java               ← NOVO

backend/src/test/java/com/dipdv/modules/
├── cashregister/service/
│   └── CashRegisterServiceTest.java          ← NOVO (8 testes)
└── payment/service/
    └── PaymentServiceTest.java               ← NOVO (10 testes)
```

---

## 7. Migration V6 (já existia)

```sql
-- V6__create_nfce_documents.sql
CREATE TYPE nfce_status AS ENUM ('PENDING', 'ISSUED', 'REJECTED', 'CANCELED');

CREATE TABLE nfce_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    order_id        UUID NOT NULL REFERENCES orders(id),
    payment_id      UUID REFERENCES payments(id),
    status          nfce_status NOT NULL DEFAULT 'PENDING',
    access_key      VARCHAR(44),
    protocol_number VARCHAR(20),
    issued_at       TIMESTAMPTZ,
    xml_content     TEXT,
    reject_reason   TEXT,
    canceled_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_nfce_order UNIQUE (order_id)
);
-- + RLS policy tenant_isolation + índices
```

---

## 8. Checklist de validação (pendente — requer Docker)

- [ ] `docker compose up -d` → banco disponível
- [ ] `.\mvnw.cmd spring-boot:run` → aplicação sobe sem erros
- [ ] Migration V6 executada automaticamente (Flyway)
- [ ] `POST /api/v1/cash-registers` → 201 `status: OPEN`
- [ ] `POST /api/v1/cash-registers/{id}/movements` → 201 (sangria)
- [ ] `POST /api/v1/payments` CASH → 201 `status: PAID`, `changeAmount >= 0`
- [ ] `POST /api/v1/payments` mesmo `idempotencyKey` → mesmo UUID (sem duplicata)
- [ ] `GET /api/v1/nfce/order/{id}` → `status: ISSUED`, `accessKey` com 44 dígitos
- [ ] `PATCH /api/v1/cash-registers/{id}/close` → `status: CLOSED`, `difference` calculado
- [ ] `SELECT action FROM audit_log` → linha `CASH_REGISTER_CLOSED`
- [ ] `POST /api/v1/payments` com caixa fechado → 409 `"Nenhum caixa aberto"`

---

## 9. Estado atual

- **Branch:** `feature/US02.1-cashregister-payment-nfce`
- **Build:** `BUILD SUCCESS` — 53 unit tests, 0 failures
- **Próximo passo:** Smoke tests com Docker + commit + PR → `develop`
