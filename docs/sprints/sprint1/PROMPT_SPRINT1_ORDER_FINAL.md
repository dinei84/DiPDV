# Relatório Final — Sprint 1: Módulo Order + AuditAspect

**Data:** 2026-04-09
**Branch:** `feature/US03.3-modifier-groups`
**Status:** CONCLUÍDO — 36 testes passando, BUILD SUCCESS

---

## 1. Resumo Executivo

Implementação completa do **módulo de pedidos (Order)** e da **camada de auditoria via AOP**.
Todos os contratos definidos no `PROMPT_SPRINT1_ORDER.md` foram entregues, incluindo:

- Entidades com preços congelados e Optimistic Locking
- Validação de modificadores (minSelect / maxSelect / maxQuantity)
- `@Aspect` de auditoria interceptando apenas operações bem-sucedidas
- Contrato `deductStockForOrder` pronto para integração com `PaymentService` (Sprint 2)
- 15 novos testes (12 unitários de serviço + 3 do AuditAspect)

---

## 2. Arquivos Criados

### 2.1 Migration de Banco de Dados

| Arquivo | Descrição |
|---|---|
| `V5__add_order_item_product_name.sql` | Adiciona coluna `product_name VARCHAR(120)` em `order_items` para snapshot do nome do produto |

**Nota:** As tabelas `orders`, `order_items`, `order_item_modifiers`, `stock_movements` e `audit_log` já existiam na `V1__initial_schema.sql`. A `V4` já havia adicionado `quantity` em `order_item_modifiers`. Apenas a coluna `product_name` estava ausente.

---

### 2.2 Módulo Order

#### Entidades (`modules/order/entity/`)

**`enums/OrderStatus.java`**
```
OPEN | CLOSED | CANCELED
```

**`Order.java`**
- Mapeada para tabela `orders`
- `@Version` para Optimistic Locking (incrementado pelo JPA a cada UPDATE)
- `cashRegisterId` nullable — CashRegister é opcional no MVP
- `cancelReason` obrigatório quando status = CANCELED (constraint no banco)
- Timestamps com `OffsetDateTime` e `@CreationTimestamp`

**`OrderItem.java`**
- `productName` — snapshot imutável do nome do produto no momento da adição
- `unitPrice` — snapshot imutável do preço no momento da adição
- `totalPrice` — calculado: `unitPrice × quantity + soma dos priceAddition dos modificadores`
- `@OneToMany(cascade = ALL, orphanRemoval = true)` com `@JoinColumn` para `OrderItemModifier`

**`OrderItemModifier.java`**
- `name` — snapshot do nome da opção no momento da adição
- `priceAddition` — snapshot do acréscimo no momento da adição
- `quantity` — quantidade efetivamente selecionada (validado contra `maxQuantity`)

---

#### Repositories (`modules/order/repository/`)

**`OrderRepository.java`**
```java
Page<Order> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable)
Page<Order> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, OrderStatus status, Pageable pageable)
Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId)
List<Order> findByTenantIdAndStatus(UUID tenantId, OrderStatus status)

@Lock(OPTIMISTIC)
Optional<Order> findByIdAndTenantIdWithLock(UUID id, UUID tenantId)
```

**`OrderItemRepository.java`**
```java
// Fetch join — busca itens + modificadores em 1 query (evita N+1)
@Query("SELECT oi FROM OrderItem oi LEFT JOIN FETCH oi.modifiers WHERE oi.orderId = :orderId ORDER BY oi.createdAt ASC")
List<OrderItem> findByOrderIdWithModifiers(UUID orderId)

long countByOrderId(UUID orderId)
Optional<OrderItem> findByIdAndOrderId(UUID id, UUID orderId)
```

---

#### DTOs (`modules/order/dto/`)

| DTO | Descrição |
|---|---|
| `CreateOrderRequest` | `cashRegisterId` (nullable) |
| `AddItemRequest` | `productId` (@NotNull), `quantity` (@Min 1), `modifiers` (lista opcional) |
| `AddItemRequest.ModifierSelectionRequest` | Record interno: `modifierOptionId`, `quantity` |
| `CancelOrderRequest` | `reason` (@NotBlank) |
| `OrderResponse` | Pedido completo com itens e modificadores |
| `OrderItemResponse` | Item com snapshots de preço e lista de modificadores |
| `OrderItemModifierResponse` | Modificador com snapshot de nome e priceAddition |
| `OrderSummaryResponse` | Listagem leve: sem itens, com `itemCount` |

---

#### Service (`modules/order/service/OrderService.java`)

| Método | Transação | Auditoria | Descrição |
|---|---|---|---|
| `createOrder` | `@Transactional` | — | Cria pedido OPEN com total = 0 |
| `getOrder` | `readOnly` | — | Busca com fetch join de itens e modificadores |
| `listOrders` | `readOnly` | — | Página com filtro opcional por status |
| `addItem` | `@Transactional` | — | Valida modificadores, congela preços, recalcula total |
| `removeItem` | `@Transactional` | — | Remove item (cascade remove modificadores), recalcula total |
| `cancelOrder` | `@Transactional` | `@Auditable(ORDER_CANCELED)` | Valida OPEN, exige reason, seta CANCELED + closedAt |
| `closeOrder` | `@Transactional` | — | Valida OPEN, seta CLOSED + closedAt |
| `deductStockForOrder` | `@Transactional` | — | Contrato para PaymentService (Sprint 2) |

**Validação de modificadores (`validateModifierSelections`):**
```
Para produto simples (sem grupos): selections deve ser vazia
Para cada ModifierGroup vinculado ao produto:
  totalQty >= group.minSelect  → senão 400
  totalQty <= group.maxSelect  → senão 400
  Para cada opção: quantity <= option.maxQuantity → senão 400
```

**`deductStockForOrder` — contrato para Sprint 2:**
- Abate `Product.stockQuantity -= item.quantity` por item
- Salva `StockMovement(type=SALE, quantity=-N, orderId=orderId)`
- Loga alerta se `stockQuantity <= stockMinLevel` após abate
- NÃO lança exceção por estoque insuficiente — venda já foi paga

---

#### Controller (`modules/order/controller/OrderController.java`)

| Método | Path | Role | Status |
|---|---|---|---|
| POST | `/api/v1/orders` | CASHIER, MANAGER, ADMIN | 201 |
| GET | `/api/v1/orders/{id}` | CASHIER, MANAGER, ADMIN | 200 |
| GET | `/api/v1/orders` | MANAGER, ADMIN | 200 (paginado) |
| POST | `/api/v1/orders/{id}/items` | CASHIER, MANAGER, ADMIN | 200 |
| DELETE | `/api/v1/orders/{id}/items/{itemId}` | CASHIER, MANAGER, ADMIN | 200 |
| PATCH | `/api/v1/orders/{id}/cancel` | MANAGER, ADMIN | 200 |
| PATCH | `/api/v1/orders/{id}/close` | CASHIER, MANAGER, ADMIN | 200 |

---

### 2.3 Camada de Auditoria (`shared/audit/`)

**`AuditAction.java`** — enum de ações auditáveis:
```
ORDER_CANCELED | ORDER_CLOSED | CASH_REGISTER_CLOSED
STOCK_ADJUSTED | PRODUCT_DELETED | USER_DEACTIVATED
```

**`@Auditable`** — anotação customizada:
```java
@Target(METHOD) @Retention(RUNTIME)
public @interface Auditable {
    AuditAction action();
    String entity();
}
```

**`AuditLog.java`** — entidade JPA → tabela `audit_log`:
- `tenantId`, `userId` (nullable — ação do sistema)
- `action` (nome do enum), `entity` (nome da tabela), `entityId`
- `payload` → `JSONB` com `method` e `args` resumidos
- `ipAddress` → tipo PostgreSQL `inet` (mapeado com `columnDefinition = "inet"`)
- Imutável: sem setters, sem `@UpdateTimestamp`

**`AuditLogRepository.java`** — `JpaRepository<AuditLog, UUID>` simples.

**`AuditAspect.java`** — coração da auditoria:
```
@AfterReturning — só audita operações BEM-SUCEDIDAS
Se o método lançar exceção → log NÃO é gravado (correto por design)
Convenção: primeiro parâmetro UUID do método = entityId
Falha interna no audit → log de erro, operação principal NÃO é derrubada
```

---

### 2.4 Módulo Inventory (base para Sprint 2)

**`modules/inventory/entity/enums/StockMovementType.java`**
```
ENTRY | LOSS | SALE
```

**`modules/inventory/entity/StockMovement.java`**
- Mapeada para tabela `stock_movements` (já existia na V1)
- Campos: `tenantId`, `productId`, `userId`, `type`, `quantity`, `reason`, `orderId`

**`modules/inventory/repository/StockMovementRepository.java`**
- `JpaRepository<StockMovement, UUID>` — base para Sprint 2

---

### 2.5 Arquivos Atualizados

**`pom.xml`**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**`GlobalExceptionHandler.java`** — novo handler adicionado:
```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiError> handleOptimisticLock(...) {
    // Retorna HTTP 409 CONFLICT com mensagem clara para o operador
}
```

---

## 3. Decisões de Implementação

### 3.1 Mapeamento `@OneToMany` sem `@ManyToOne`
O `OrderItemModifier` armazena `UUID orderItemId` como campo simples (sem `@ManyToOne`). O `OrderItem` gerencia a coleção via `@OneToMany(cascade = ALL) @JoinColumn(name = "order_item_id")`. Isso evita lazy loading acidental e mantém o módulo de pedidos desacoplado.

### 3.2 Tipo `inet` no PostgreSQL
A coluna `ip_address` na tabela `audit_log` usa o tipo nativo `inet` do PostgreSQL. A entidade `AuditLog` usa `@Column(columnDefinition = "inet")` para que a validação de schema do Hibernate reconheça o tipo correto, evitando o erro:
```
Schema-validation: wrong column type encountered in column [ip_address] in table [audit_log];
found [inet (Types#OTHER)], but expecting [varchar(255) (Types#VARCHAR)]
```

### 3.3 `OrderSummaryResponse.itemCount`
Para a listagem paginada, o `itemCount` é obtido via `orderItemRepository.countByOrderId(orderId)` por pedido. Para MVP (páginas de 20 pedidos) o custo é aceitável. Em Sprint 3 pode ser substituído por uma query com `COUNT` agregado.

### 3.4 `userId` no `deductStockForOrder`
Como o `StockMovement` exige `user_id NOT NULL`, e o método será chamado pelo `PaymentService` em contexto autenticado (Sprint 2), o `userId` é extraído do pedido original (`order.getUserId()`), garantindo rastreabilidade mesmo em chamadas assíncronas.

### 3.5 Auditoria — `@AfterReturning` vs `@Around`
Usamos `@AfterReturning` deliberadamente: o log só é gravado após a execução bem-sucedida. Se o método lançar exceção (ex: pedido não encontrado), nenhum registro é criado no `audit_log`. Isso é correto por design — não auditamos operações que falharam.

---

## 4. Relatório de Testes

### 4.1 Resultado Final

```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 4.2 Breakdown por Suite

| Suite de Testes | Testes | Resultado | Tempo |
|---|---|---|---|
| `DiPdvApplicationTests` | 1 | PASS | 17.70s |
| `CatalogServiceTest` | 8 | PASS | 1.12s |
| `ModifierServiceTest` | 10 | PASS | 0.82s |
| `OrderServiceTest` | 12 | PASS | 0.44s |
| `AuditAspectTest` | 3 | PASS | 0.33s |
| `TenantContextServiceTest` | 2 | PASS | 0.30s |
| **Total** | **36** | **PASS** | ~21s |

### 4.3 Testes Novos — `OrderServiceTest` (12 testes)

| Teste | Cenário | Resultado |
|---|---|---|
| `createOrder_whenValid_shouldReturnOpenOrder` | Pedido criado com status OPEN, total 0, version 0 | PASS |
| `getOrder_whenNotFound_shouldThrowNotFound` | Pedido inexistente lança 404 | PASS |
| `addItem_whenOrderClosed_shouldThrowConflict` | Adicionar item em pedido CLOSED lança 409 | PASS |
| `addItem_whenProductSimple_shouldCreateItemWithoutModifiers` | Item sem modificadores criado corretamente | PASS |
| `addItem_whenModifierQuantityExceedsMax_shouldThrowBadRequest` | qty=3 em opção com maxQuantity=2 lança 400 | PASS |
| `addItem_whenRequiredGroupNotSelected_shouldThrowBadRequest` | Grupo com minSelect=1 sem seleção lança 400 | PASS |
| `addItem_whenValid_shouldFreezeProductPrice` | unitPrice = preço do produto no momento da adição | PASS |
| `removeItem_whenOrderOpen_shouldRecalculateTotal` | Item removido, total recalculado, cascade nos modificadores | PASS |
| `cancelOrder_whenAlreadyCanceled_shouldThrowConflict` | Cancelar pedido já cancelado lança 409 | PASS |
| `cancelOrder_whenReasonBlank_shouldThrowBadRequest` | Cancelar sem motivo lança 400 | PASS |
| `cancelOrder_whenValid_shouldSetStatusAndReason` | Status CANCELED, reason e closedAt setados | PASS |
| `deductStockForOrder_shouldCreateStockMovementPerItem` | StockMovement criado, stockQuantity abatido por item | PASS |

### 4.4 Testes Novos — `AuditAspectTest` (3 testes)

| Teste | Cenário | Resultado |
|---|---|---|
| `auditAspect_whenMethodSucceeds_shouldSaveAuditLog` | Método bem-sucedido → `auditLogRepository.save()` chamado | PASS |
| `auditAspect_whenTenantContextEmpty_shouldNotBreakOperation` | TenantContext null → não lança exceção, save ainda chamado | PASS |
| `auditAspect_whenRepositoryThrows_shouldNotPropagateException` | Falha no save → não propaga para a operação principal | PASS |

---

## 5. Checklist Final

- [x] `./mvnw.cmd test` — 36 testes passando (21 anteriores + 15 novos)
- [x] `POST /orders` → 201 com status OPEN e version 0
- [x] `POST /orders/{id}/items` produto simples → total atualizado
- [x] `POST /orders/{id}/items` com modificador → totalPrice correto (unit_price + priceAddition × qty)
- [x] `PATCH /orders/{id}/cancel` → status CANCELED (body: `{"reason": "..."}`)
- [x] `audit_log` gravado pelo `@Aspect` após cancelamento bem-sucedido
- [x] Preço congelado: `unitPrice` = snapshot do `Product.price` no momento da adição
- [x] 409 CONFLICT em edição simultânea (`ObjectOptimisticLockingFailureException`)
- [x] `deductStockForOrder()` implementado e testado — pronto para `PaymentService`
- [x] `spring-boot-starter-aop` adicionado ao `pom.xml`
- [x] `GlobalExceptionHandler` com handler de Optimistic Lock → HTTP 409
- [x] Módulo Inventory base criado (`StockMovement` + `StockMovementType`)

---

## 6. O que NÃO foi implementado (escopo Sprint 2+)

| Item | Sprint |
|---|---|
| `PaymentService` e integração com `deductStockForOrder` | Sprint 2 |
| `CashRegisterService` (vínculo obrigatório de caixa) | Sprint 2 |
| Relatórios e dashboard | Sprint 3 |
| `@Auditable` em `closeOrder` (apenas `cancelOrder` por ora) | Sprint 2 |

---

## 7. Estrutura Final Criada

```
backend/src/main/java/com/dipdv/
├── modules/
│   ├── inventory/
│   │   ├── entity/
│   │   │   ├── StockMovement.java
│   │   │   └── enums/StockMovementType.java
│   │   └── repository/StockMovementRepository.java
│   └── order/
│       ├── controller/OrderController.java
│       ├── dto/
│       │   ├── AddItemRequest.java          (com ModifierSelectionRequest interno)
│       │   ├── CancelOrderRequest.java
│       │   ├── CreateOrderRequest.java
│       │   ├── OrderItemModifierResponse.java
│       │   ├── OrderItemResponse.java
│       │   ├── OrderResponse.java
│       │   └── OrderSummaryResponse.java
│       ├── entity/
│       │   ├── Order.java
│       │   ├── OrderItem.java
│       │   ├── OrderItemModifier.java
│       │   └── enums/OrderStatus.java
│       ├── repository/
│       │   ├── OrderItemRepository.java
│       │   └── OrderRepository.java
│       └── service/OrderService.java
└── shared/
    └── audit/
        ├── Auditable.java
        ├── AuditAction.java
        ├── AuditAspect.java
        ├── AuditLog.java
        └── AuditLogRepository.java

backend/src/main/resources/db/migration/
└── V5__add_order_item_product_name.sql

backend/src/test/java/com/dipdv/
├── modules/order/service/OrderServiceTest.java    (12 testes)
└── shared/audit/AuditAspectTest.java              (3 testes)
```
