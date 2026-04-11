# Prompt — Antigravity: Sprint 2 — CashRegister + Payment + NFC-e

---

## Contexto

Sprint 1 concluído com 36 testes passando. Este prompt implementa os três
módulos do Sprint 2 em sequência obrigatória:

```
1. CashRegister  →  2. Payment  →  3. NFC-e (mock)
```

A ordem importa: `PaymentService` depende de `CashRegisterService`,
e `NfceService` é disparado pelo `PaymentService`.

**Branch:** `feature/US02.1-cashregister-payment-nfce` a partir de `develop`
**Commits:** `feat(cashregister): ...`, `feat(payment): ...`, `feat(nfce): ...`

---

## Decisões de design — ler antes de codar

### 1. CashRegister é obrigatório para Payment
`PaymentService.registerPayment()` valida que existe um `CashRegister`
com `status = OPEN` para o tenant atual antes de processar qualquer pagamento.
Sem caixa aberto → `BusinessException(409)`.

### 2. Pagamento misto suportado
Um `Order` pode ter múltiplos `Payment` (ex: R$10 em dinheiro + R$8,90 em Pix).
Regra: soma de todos os `Payment` com `status = PAID` de um `Order`
não pode exceder `order.total`. Ao atingir `order.total` exato, o pedido
já está quitado — pagamentos adicionais são bloqueados com 409.

### 3. Idempotência no Payment
O frontend gera um `idempotency_key` (UUID v4) antes de enviar o request.
O backend verifica se a chave já existe no tenant:
- Se sim → retorna o `Payment` original sem reprocessar
- Se não → processa normalmente

Isso protege contra duplo clique e retentativas de rede no Pix.

### 4. Fluxo de status do Payment
```
PENDING → PAID      (confirmado)
PENDING → FAILED    (erro no processamento — apenas para gateway real)
PAID    → REFUNDED  (estorno — pós-MVP, modelado mas não implementado)
PAID/PENDING → CANCELED (cancelado pelo operador antes da confirmação)
```
No mock: todo PENDING vai direto para PAID após validação.

### 5. NFC-e automática após PAID
Após confirmar o pagamento (`status = PAID`), o `PaymentService` chama
`NfceService.emit(orderId)` de forma síncrona no MVP.
Se a emissão falhar, o pagamento **não é revertido** — a venda foi confirmada.
O erro de NFC-e é logado e o `NfceDocument.status` fica como `REJECTED`.

### 6. NFC-e — interface + mock
`NfceService` é uma interface com dois métodos: `emit` e `cancel`.
`MockNfceService` implementa simulando resposta da SEFAZ.
Quando integrar com Nuvem Fiscal no futuro:
criar `NuvemFiscalNfceService implements NfceService` e trocar o `@Primary`.

```java
// TODO: substituir por NuvemFiscalNfceService quando tiver certificado digital
// Referência: https://dev.nuvemfiscal.com.br/docs/api/#tag/NFC-e
@Primary
@Service
public class MockNfceService implements NfceService { ... }
```

### 7. Migration V6 — NfceDocument
A tabela `nfce_documents` não existe ainda — criar via migration.

---

## Migration V6

**Arquivo:** `resources/db/migration/V6__create_nfce_documents.sql`

```sql
-- =============================================================================
-- DiPDV — V6__create_nfce_documents.sql
-- Tabela de documentos NFC-e vinculados a pedidos
-- =============================================================================

CREATE TYPE nfce_status AS ENUM (
    'PENDING',    -- aguardando envio à SEFAZ
    'ISSUED',     -- emitida com sucesso
    'REJECTED',   -- rejeitada pela SEFAZ (erro nos dados)
    'CANCELED'    -- cancelada após emissão
);

CREATE TABLE nfce_documents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    order_id        UUID        NOT NULL REFERENCES orders(id)  ON DELETE RESTRICT,
    payment_id      UUID        REFERENCES payments(id)         ON DELETE SET NULL,
    status          nfce_status NOT NULL DEFAULT 'PENDING',
    access_key      VARCHAR(44),           -- chave de acesso SEFAZ (44 dígitos)
    protocol_number VARCHAR(20),           -- número do protocolo de autorização
    issued_at       TIMESTAMPTZ,           -- data/hora de autorização pela SEFAZ
    xml_content     TEXT,                  -- XML da NFC-e (mock ou real)
    reject_reason   TEXT,                  -- motivo de rejeição se status = REJECTED
    canceled_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_nfce_order UNIQUE (order_id)  -- 1 NFC-e por pedido
);

-- RLS
ALTER TABLE nfce_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE nfce_documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON nfce_documents
    USING      (tenant_id = current_setting('app.current_tenant', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::UUID);

-- Índices
CREATE INDEX idx_nfce_tenant_status  ON nfce_documents (tenant_id, status);
CREATE INDEX idx_nfce_order_id       ON nfce_documents (order_id);

COMMENT ON TABLE  nfce_documents            IS 'Documentos NFC-e emitidos por pedido';
COMMENT ON COLUMN nfce_documents.access_key IS 'Chave de 44 dígitos gerada pela SEFAZ. Mock: UUID sem hífens + zeros.';
```

---

## Estrutura a criar

```
modules/cashregister/
├── entity/
│   ├── CashRegister.java
│   ├── CashMovement.java
│   └── enums/
│       ├── CashRegisterStatus.java
│       └── CashMovementType.java
├── repository/
│   ├── CashRegisterRepository.java
│   └── CashMovementRepository.java
├── dto/
│   ├── OpenCashRegisterRequest.java
│   ├── CashRegisterResponse.java
│   ├── CashMovementRequest.java
│   ├── CashMovementResponse.java
│   └── CloseCashRegisterRequest.java
├── service/
│   └── CashRegisterService.java
└── controller/
    └── CashRegisterController.java

modules/payment/
├── entity/
│   ├── Payment.java
│   └── enums/
│       ├── PaymentMethod.java
│       └── PaymentStatus.java
├── repository/
│   └── PaymentRepository.java
├── dto/
│   ├── RegisterPaymentRequest.java
│   └── PaymentResponse.java
├── service/
│   └── PaymentService.java
└── controller/
    └── PaymentController.java

modules/nfce/
├── entity/
│   ├── NfceDocument.java
│   └── enums/NfceStatus.java
├── repository/
│   └── NfceDocumentRepository.java
├── dto/
│   └── NfceResponse.java
├── service/
│   ├── NfceService.java              ← interface
│   └── MockNfceService.java          ← implementação mock
└── controller/
    └── NfceController.java

test/java/com/dipdv/
├── modules/cashregister/service/
│   └── CashRegisterServiceTest.java
└── modules/payment/service/
    └── PaymentServiceTest.java
```

---

## Tarefa 1 — Módulo CashRegister

### Entidades

**`CashRegisterStatus.java`:** `OPEN | CLOSED`
**`CashMovementType.java`:** `SUPPLY | BLEEDING`

**`CashRegister.java`:**
```
id, tenantId, openedBy (UUID), closedBy (UUID nullable),
status (CashRegisterStatus — @JdbcTypeCode NAMED_ENUM),
openingBalance (BigDecimal DEFAULT 0),
closingBalance (BigDecimal nullable — calculado no fechamento),
physicalBalance (BigDecimal nullable — informado pelo operador),
difference (BigDecimal nullable — physicalBalance - closingBalance),
totalCash (BigDecimal DEFAULT 0),
totalPix  (BigDecimal DEFAULT 0),
openedAt  (OffsetDateTime @CreationTimestamp),
closedAt  (OffsetDateTime nullable)
```

> Não usar `@Builder.Default` em `totalCash/totalPix/openingBalance` —
> inicializar com `BigDecimal.ZERO` explicitamente no Service ao criar.

**`CashMovement.java`:**
```
id, cashRegisterId (UUID), userId (UUID),
type (CashMovementType — @JdbcTypeCode NAMED_ENUM),
amount (BigDecimal NOT NULL > 0),
description (VARCHAR 255 NOT NULL),
createdAt
```

### Repositories

**`CashRegisterRepository.java`:**
```java
// Buscar caixa aberto do tenant (exclusion constraint no banco garante no máx 1)
Optional<CashRegister> findByTenantIdAndStatus(UUID tenantId, CashRegisterStatus status);

// Histórico paginado
Page<CashRegister> findByTenantIdOrderByOpenedAtDesc(UUID tenantId, Pageable pageable);

Optional<CashRegister> findByIdAndTenantId(UUID id, UUID tenantId);
```

**`CashMovementRepository.java`:**
```java
List<CashMovement> findByCashRegisterIdOrderByCreatedAtAsc(UUID cashRegisterId);

// Soma de movimentações por tipo (para totalizar no fechamento)
@Query("""
    SELECT SUM(cm.amount) FROM CashMovement cm
    WHERE cm.cashRegisterId = :registerId AND cm.type = :type
""")
Optional<BigDecimal> sumAmountByRegisterIdAndType(
    @Param("registerId") UUID registerId,
    @Param("type") CashMovementType type
);
```

### CashRegisterService

Métodos obrigatórios:

```java
// Abrir caixa — valida que não há caixa aberto no tenant
@Transactional
public CashRegisterResponse openCashRegister(OpenCashRegisterRequest request)
// Regra: já existe OPEN → BusinessException(409, "Já existe um caixa aberto")
// userId vem do SecurityContextHolder (DiPdvAuthDetails)

// Buscar caixa aberto atual
@Transactional(readOnly = true)
public CashRegisterResponse getOpenRegister()
// 404 se não houver caixa aberto

// Registrar sangria ou suprimento
@Transactional
public CashMovementResponse addMovement(UUID registerId, CashMovementRequest request)
// Regra: caixa deve estar OPEN
// Regra: amount > 0

// Fechar caixa
@Transactional
@Auditable(action = AuditAction.CASH_REGISTER_CLOSED, entity = "cash_registers")
public CashRegisterResponse closeCashRegister(UUID registerId, CloseCashRegisterRequest request)
// 1. Validar status OPEN
// 2. Calcular closingBalance:
//      openingBalance
//      + totalCash (acumulado nos pagamentos)
//      + totalPix  (acumulado nos pagamentos)
//      + soma SUPPLY
//      - soma BLEEDING
// 3. difference = request.physicalBalance - closingBalance
// 4. Setar status CLOSED, closedAt, closedBy
// 5. @Auditable → audit_log

// Listar histórico de caixas
@Transactional(readOnly = true)
public Page<CashRegisterResponse> listRegisters(Pageable pageable)
```

### DTOs

**`OpenCashRegisterRequest`:** `openingBalance` BigDecimal `@DecimalMin("0.00")`

**`CloseCashRegisterRequest`:** `physicalBalance` BigDecimal `@NotNull @DecimalMin("0.00")`

**`CashRegisterResponse`:** `id`, `status`, `openingBalance`, `closingBalance`,
`physicalBalance`, `difference`, `totalCash`, `totalPix`,
`openedAt`, `closedAt`, `movements` (`List<CashMovementResponse>`)

**`CashMovementRequest`:** `type` `@NotNull`, `amount` `@DecimalMin("0.01")`, `description` `@NotBlank`

**`CashMovementResponse`:** `id`, `type`, `amount`, `description`, `createdAt`

### CashRegisterController

```
@RestController @RequestMapping("/api/v1/cash-registers")
@Tag(name = "Caixa", description = "Controle de turno e movimentações")
```

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/` | CASHIER | Abrir caixa |
| GET | `/current` | CASHIER | Caixa aberto atual |
| GET | `/` | MANAGER | Histórico paginado |
| GET | `/{id}` | MANAGER | Buscar por id |
| POST | `/{id}/movements` | CASHIER | Sangria / suprimento |
| PATCH | `/{id}/close` | MANAGER | Fechar caixa |

---

## Tarefa 2 — Módulo Payment

### Entidades

**`PaymentMethod.java`:** `CASH | PIX`
**`PaymentStatus.java`:** `PENDING | PAID | FAILED | CANCELED | REFUNDED`

**`Payment.java`:**
```
id, tenantId, orderId (UUID), cashRegisterId (UUID),
method (PaymentMethod — @JdbcTypeCode NAMED_ENUM),
status (PaymentStatus — @JdbcTypeCode NAMED_ENUM DEFAULT PENDING),
amount (BigDecimal NOT NULL > 0),
changeAmount (BigDecimal DEFAULT 0 — troco para CASH),
idempotencyKey (VARCHAR 64 NOT NULL),
gatewayRef (VARCHAR 120 nullable — txid Pix, NSU TEF),
createdAt, updatedAt (@UpdateTimestamp)
```

### PaymentRepository

```java
// Buscar por idempotency_key (validação de duplicidade)
Optional<Payment> findByTenantIdAndIdempotencyKey(UUID tenantId, String key);

// Soma de pagamentos PAID de um pedido (para validar pagamento misto)
@Query("""
    SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
    WHERE p.orderId = :orderId AND p.status = 'PAID'
""")
BigDecimal sumPaidAmountByOrderId(@Param("orderId") UUID orderId);

// Listar pagamentos de um pedido
List<Payment> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);

Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);
```

### PaymentService — o mais complexo do Sprint 2

```java
@Transactional
public PaymentResponse registerPayment(RegisterPaymentRequest request)
```

**Fluxo interno obrigatório (em ordem):**

```
1. IDEMPOTÊNCIA
   Buscar payment por (tenantId, idempotencyKey)
   Se encontrado → retornar payment existente sem processar (HTTP 200)

2. VALIDAR CAIXA ABERTO
   Buscar CashRegister com status OPEN para o tenant
   Não encontrado → BusinessException(409, "Nenhum caixa aberto")

3. VALIDAR ORDER
   Buscar Order por id + tenantId → 404 se não encontrado
   Order.status != CLOSED → BusinessException(409, "Pedido não está fechado")

4. VALIDAR VALOR TOTAL
   somaPaga = sumPaidAmountByOrderId(orderId)
   somaPaga + request.amount > order.total
   → BusinessException(409, "Valor excede o total do pedido")

5. CALCULAR TROCO (apenas CASH)
   changeAmount = request.amount - (order.total - somaPaga)
   Se changeAmount < 0 → 0 (pagamento parcial, não há troco ainda)

6. CRIAR PAYMENT com status PENDING

7. SIMULAR GATEWAY (mock)
   Para CASH: confirmar imediatamente → PAID
   Para PIX:  gerar mock QR code (string base64 simulada) → PAID
              gatewayRef = "PIX-MOCK-" + UUID.randomUUID()

8. ATUALIZAR TOTALIZADORES DO CAIXA
   Se CASH: cashRegister.totalCash += payment.amount
   Se PIX:  cashRegister.totalPix  += payment.amount
   Salvar cashRegister

9. VERIFICAR SE PEDIDO ESTÁ QUITADO
   novaSomaPaga = somaPaga + payment.amount
   Se novaSomaPaga >= order.total:
     → chamar orderService.deductStockForOrder(orderId)  ← Sprint 1

10. EMITIR NFC-e (automático)
    Se novaSomaPaga >= order.total:
      → nfceService.emit(orderId, payment.id)
    Falha na emissão: logar erro, NÃO reverter pagamento

11. RETORNAR PaymentResponse
```

```java
// Cancelar pagamento (apenas PENDING)
@Transactional
public PaymentResponse cancelPayment(UUID paymentId)
// PAID não pode ser cancelado via este endpoint — apenas via estorno (pós-MVP)
// Regra: reverter totalCash/totalPix do caixa se cancelar

// Listar pagamentos de um pedido
@Transactional(readOnly = true)
public List<PaymentResponse> listOrderPayments(UUID orderId)
```

### DTOs

**`RegisterPaymentRequest`:**
```java
public record RegisterPaymentRequest(
    @NotNull UUID orderId,
    @NotNull PaymentMethod method,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(max = 64) String idempotencyKey
) {}
```

**`PaymentResponse`:**
`id`, `orderId`, `method`, `status`, `amount`, `changeAmount`,
`gatewayRef`, `idempotencyKey`, `createdAt`

> Para PIX: incluir campo `pixQrCode` (String nullable)
> no response — preenchido com mock no Sprint 2,
> com QR Code real no Sprint 3+.

### PaymentController

```
@RestController @RequestMapping("/api/v1/payments")
@Tag(name = "Pagamentos", description = "Registro e consulta de pagamentos")
```

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/` | CASHIER | Registrar pagamento |
| GET | `/order/{orderId}` | CASHIER | Pagamentos de um pedido |
| PATCH | `/{id}/cancel` | MANAGER | Cancelar pagamento PENDING |

---

## Tarefa 3 — Módulo NFC-e

### NfceService (interface)

```java
package com.dipdv.modules.nfce.service;

import java.util.UUID;

/**
 * Contrato para emissão de NFC-e.
 *
 * Implementação atual: MockNfceService (simula SEFAZ)
 * Implementação futura: NuvemFiscalNfceService
 *   Referência: https://dev.nuvemfiscal.com.br/docs/api/#tag/NFC-e
 *
 * Para trocar: criar NuvemFiscalNfceService implements NfceService,
 * anotar com @Primary e remover @Primary do MockNfceService.
 */
public interface NfceService {

    /**
     * Emite NFC-e para um pedido já pago.
     * @param orderId  ID do pedido
     * @param paymentId ID do pagamento que quitou o pedido
     * @return NfceDocument gerado
     */
    NfceDocument emit(UUID orderId, UUID paymentId);

    /**
     * Cancela uma NFC-e emitida.
     * Só é possível dentro da janela de cancelamento (30min no SEFAZ real).
     */
    NfceDocument cancel(UUID nfceDocumentId, String reason);
}
```

### MockNfceService

```java
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class MockNfceService implements NfceService {

    private final NfceDocumentRepository nfceDocumentRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public NfceDocument emit(UUID orderId, UUID paymentId) {
        UUID tenantId = TenantContext.getRequired();

        // Verificar se já existe NFC-e para este pedido
        return nfceDocumentRepository.findByOrderId(orderId)
            .orElseGet(() -> {
                // Simular chave de acesso SEFAZ (44 dígitos)
                // Formato real: cUF(2) + AAMM(4) + CNPJ(14) + mod(2) + serie(3) + nNF(9) + tpEmis(1) + cNF(8) + cDV(1)
                // Mock: preencher com dados fictícios mas no formato correto
                String mockAccessKey = generateMockAccessKey();
                String mockProtocol  = "135" + System.currentTimeMillis();

                NfceDocument doc = NfceDocument.builder()
                    .tenantId(tenantId)
                    .orderId(orderId)
                    .paymentId(paymentId)
                    .status(NfceStatus.ISSUED)
                    .accessKey(mockAccessKey)
                    .protocolNumber(mockProtocol)
                    .issuedAt(OffsetDateTime.now())
                    .xmlContent(generateMockXml(orderId, mockAccessKey))
                    .build();

                log.info("[MOCK NFC-e] Emitida para orderId={} accessKey={}",
                    orderId, mockAccessKey);

                return nfceDocumentRepository.save(doc);
            });
    }

    @Override
    @Transactional
    public NfceDocument cancel(UUID nfceDocumentId, String reason) {
        NfceDocument doc = nfceDocumentRepository
            .findByIdAndTenantId(nfceDocumentId, TenantContext.getRequired())
            .orElseThrow(() -> new BusinessException(
                "NFC-e não encontrada", HttpStatus.NOT_FOUND));

        if (doc.getStatus() != NfceStatus.ISSUED) {
            throw new BusinessException(
                "Apenas NFC-e emitidas podem ser canceladas", HttpStatus.CONFLICT);
        }

        doc.setStatus(NfceStatus.CANCELED);
        doc.setCanceledAt(OffsetDateTime.now());
        log.info("[MOCK NFC-e] Cancelada: id={} reason={}", nfceDocumentId, reason);
        return nfceDocumentRepository.save(doc);
    }

    private String generateMockAccessKey() {
        // 44 dígitos numéricos — formato SEFAZ simplificado para mock
        return String.format("35%s%s%s",
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")),
            "00000000000000",           // CNPJ mock
            String.valueOf(System.currentTimeMillis()).substring(0, 26)
        ).replaceAll("[^0-9]", "0").substring(0, 44);
    }

    private String generateMockXml(UUID orderId, String accessKey) {
        return String.format(
            "<nfeProc versao=\"4.00\"><NFe><infNFe Id=\"NFe%s\">" +
            "<ide><cUF>35</cUF><mod>65</mod></ide>" +
            "<emit><CNPJ>00000000000000</CNPJ><xNome>DiPDV Mock</xNome></emit>" +
            "</infNFe></NFe></nfeProc>", accessKey);
    }
}
```

### NfceDocument entity

```
id, tenantId, orderId (UNIQUE), paymentId (nullable),
status (NfceStatus — @JdbcTypeCode NAMED_ENUM),
accessKey (VARCHAR 44 nullable),
protocolNumber (VARCHAR 20 nullable),
issuedAt (nullable), xmlContent (TEXT nullable),
rejectReason (TEXT nullable), canceledAt (nullable),
createdAt, updatedAt
```

### NfceController

```
@RestController @RequestMapping("/api/v1/nfce")
@Tag(name = "NFC-e", description = "Nota Fiscal do Consumidor Eletrônica")
```

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/order/{orderId}` | CASHIER | Buscar NFC-e de um pedido |
| PATCH | `/{id}/cancel` | MANAGER | Cancelar NFC-e emitida |

> Não há endpoint de emissão manual — a emissão é automática via PaymentService.

---

## Tarefa 4 — Testes unitários

### CashRegisterServiceTest (8 cenários)

```java
openCashRegister_whenNoOpenRegister_shouldReturnOpenStatus()
openCashRegister_whenAlreadyOpen_shouldThrowConflict()
addMovement_whenRegisterClosed_shouldThrowConflict()
addMovement_whenAmountZero_shouldThrowBadRequest()
closeCashRegister_whenOpen_shouldCalculateClosingBalance()
closeCashRegister_whenAlreadyClosed_shouldThrowConflict()
getOpenRegister_whenExists_shouldReturn()
getOpenRegister_whenNone_shouldThrowNotFound()
```

### PaymentServiceTest (10 cenários)

```java
registerPayment_whenIdempotencyKeyExists_shouldReturnExistingPayment()
registerPayment_whenNoCashRegisterOpen_shouldThrowConflict()
registerPayment_whenOrderNotClosed_shouldThrowConflict()
registerPayment_whenAmountExceedsTotal_shouldThrowConflict()
registerPayment_whenCash_shouldCalculateChange()
registerPayment_whenPix_shouldGenerateMockGatewayRef()
registerPayment_whenOrderFullyPaid_shouldCallDeductStock()
registerPayment_whenOrderFullyPaid_shouldCallNfceEmit()
registerPayment_whenNfceEmitFails_shouldNotRevertPayment()
cancelPayment_whenStatusPaid_shouldThrowConflict()
```

---

## Tarefa 5 — Validação

### 5a. Build e testes

```bash
cd backend
.\mvnw.cmd test
```

Esperado: mínimo **54 testes** (36 anteriores + 18 novos).

### 5b. Smoke tests completos

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
```

**[1] Abrir caixa:**
```bash
curl -s -X POST http://localhost:8080/api/v1/cash-registers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"openingBalance": 100.00}' | jq .
```
Esperado: 201, `status: "OPEN"`.

**[2] Registrar sangria:**
```bash
REGISTER_ID="UUID_DO_CAIXA"
curl -s -X POST \
  "http://localhost:8080/api/v1/cash-registers/$REGISTER_ID/movements" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"type":"BLEEDING","amount":20.00,"description":"Retirada para troco"}' | jq .
```
Esperado: 201.

**[3] Criar e fechar pedido:**
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}' | jq -r .id)

PRODUCT_ID="UUID_PRODUTO_XBURGUER"
curl -s -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":"'$PRODUCT_ID'","quantity":1,"modifiers":[]}' | jq .total

curl -s -X PATCH "http://localhost:8080/api/v1/orders/$ORDER_ID/close" \
  -H "Authorization: Bearer $TOKEN" | jq .status
```

**[4] Pagamento em dinheiro (deve gerar NFC-e automática):**
```bash
IDEMPOTENCY_KEY=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": "'$ORDER_ID'",
    "method": "CASH",
    "amount": 18.90,
    "idempotencyKey": "'$IDEMPOTENCY_KEY'"
  }' | jq .
```
Esperado: `status: "PAID"`, `changeAmount >= 0`.

**[5] Idempotência — mesmo key deve retornar o payment original:**
```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": "'$ORDER_ID'",
    "method": "CASH",
    "amount": 18.90,
    "idempotencyKey": "'$IDEMPOTENCY_KEY'"
  }' | jq .id
```
Esperado: **mesmo UUID** do payment anterior — não criou duplicata.

**[6] NFC-e gerada automaticamente:**
```bash
curl -s "http://localhost:8080/api/v1/nfce/order/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: `status: "ISSUED"`, `accessKey` com 44 dígitos.

**[7] Verificar audit_log do fechamento de caixa:**
```bash
curl -s -X PATCH \
  "http://localhost:8080/api/v1/cash-registers/$REGISTER_ID/close" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"physicalBalance": 98.90}' | jq .

docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT action, entity, created_at FROM audit_log ORDER BY created_at DESC LIMIT 3;"
```
Esperado: linha com `CASH_REGISTER_CLOSED`.

**[8] Tentar pagar com caixa fechado (deve retornar 409):**
```bash
NEW_KEY=$(uuidgen)
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId":"'$ORDER_ID'",
    "method":"PIX",
    "amount":10.00,
    "idempotencyKey":"'$NEW_KEY'"
  }' | jq .
```
Esperado: 409, `"Nenhum caixa aberto"`.

---

## Tarefa 6 — Commit

```bash
git add .
git commit -m "feat(payment): implementar CashRegister, Payment e NFC-e mock

- CashRegister: abertura/fechamento de turno, sangria/suprimento
- CashRegister: @Auditable no fechamento → audit_log
- PaymentService: fluxo completo CASH e PIX com idempotência
- PaymentService: integração com deductStockForOrder (Sprint 1)
- PaymentService: disparo automático de NFC-e após PAID
- MockNfceService: simulação de SEFAZ com chave de 44 dígitos
- NfceDocument: entity + migration V6
- Migration V6: tabela nfce_documents com RLS
- 18 novos testes (CashRegisterServiceTest + PaymentServiceTest)

Closes #XX (US02.1, US02.2, US02.3, US02.4)"

git push origin feature/US02.1-cashregister-payment-nfce
```

---

## Checklist final

- [ ] `.\mvnw.cmd test` — mínimo 54 testes passando
- [ ] Migration V6 executada (tabela `nfce_documents` criada)
- [ ] `POST /cash-registers` → 201 OPEN
- [ ] `POST /cash-registers/{id}/movements` → 201 (sangria)
- [ ] `POST /payments` CASH → PAID + changeAmount
- [ ] Idempotência: mesmo key → mesmo UUID de payment
- [ ] `GET /nfce/order/{id}` → ISSUED com accessKey 44 dígitos
- [ ] `PATCH /cash-registers/{id}/close` → CLOSED + difference calculada
- [ ] `audit_log` com CASH_REGISTER_CLOSED (evidência via psql)
- [ ] Pagamento com caixa fechado → 409
- [ ] PR aberto `feature/US02.1-cashregister-payment-nfce` → `develop`

---

## O que NÃO implementar aqui

- Gateway real (Stone, PagBank) — pós-MVP
- NFC-e real via Nuvem Fiscal — pós-MVP
- Cartão (TEF físico) — pós-MVP
- Relatórios de vendas — Sprint 3
- Dashboard — Sprint 3
