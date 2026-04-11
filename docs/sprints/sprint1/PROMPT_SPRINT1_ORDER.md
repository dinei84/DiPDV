# Prompt — Antigravity: Sprint 1 — Módulo Order + AuditAspect

---

## Contexto

Catalog e Modifiers entregues. Este prompt implementa o **módulo Order** —
o coração do PDV — e a **camada de auditoria via AOP**.

É o prompt mais complexo do projeto. Ler inteiro antes de começar.

**Branch:** `feature/US01.1-order-module` a partir de `develop`
**Commits:** `feat(order): ...` e `feat(audit): ...`

---

## Decisões de design — ler antes de codar

### 1. CashRegister é opcional no MVP
`Order.cashRegisterId` é nullable. O PDV pode abrir pedidos sem caixa
vinculado. O vínculo será obrigatório no módulo CashRegister (Sprint 2).

### 2. Estoque abatido apenas no PAID
O `OrderService` expõe o método `deductStockForOrder(UUID orderId)`.
Este método **não é chamado pelo OrderService** — será chamado pelo
`PaymentService` quando o pagamento mudar para `PAID` (Sprint 2).
Implementar o método agora, documentar o contrato, não conectar ainda.

### 3. Preços congelados — regra absoluta
Ao adicionar um item ao pedido:
- `unit_price` = `Product.price` no momento da adição
- `total_price` = `unit_price × quantity + soma dos price_addition dos modificadores`
- `OrderItemModifier.priceAddition` = `ModifierOption.priceAddition` no momento
- `OrderItemModifier.name` = `ModifierOption.name` no momento

**Nunca recalcular com preços atuais do produto após o pedido ser fechado.**

### 4. Optimistic Locking em Order
`Order.version` com `@Version` — o JPA incrementa automaticamente.
Conflito gera `OptimisticLockException` → tratar no Controller como HTTP 409.

### 5. Validação de modificadores ao adicionar item
```
Para cada ModifierGroup vinculado ao produto:
  - Contar opções selecionadas
  - total_selecionado >= group.minSelect  → senão 400
  - total_selecionado <= group.maxSelect  → senão 400
  - Para cada opção: quantity <= option.maxQuantity → senão 400
```
Produto sem grupos (simples) → nenhuma validação de modificador necessária.

### 6. @Aspect de auditoria — padrão AOP
A anotação `@Auditable` marca métodos de Service que devem ser logados.
O `AuditAspect` intercepta após a execução bem-sucedida (`@AfterReturning`)
e grava em `audit_log`. **Nunca gravar se o método lançar exceção** —
não auditar operações que falharam.

---

## Estrutura a criar

```
modules/order/
├── entity/
│   ├── Order.java
│   ├── OrderItem.java
│   └── OrderItemModifier.java
├── repository/
│   ├── OrderRepository.java
│   └── OrderItemRepository.java
├── dto/
│   ├── CreateOrderRequest.java
│   ├── AddItemRequest.java
│   │   └── ModifierSelectionRequest.java  (record interno)
│   ├── OrderResponse.java
│   ├── OrderItemResponse.java
│   └── OrderSummaryResponse.java          (listagem — sem itens)
├── service/
│   └── OrderService.java
└── controller/
    └── OrderController.java

shared/audit/
├── Auditable.java                         (@interface — anotação customizada)
├── AuditAction.java                       (enum de ações auditáveis)
├── AuditLog.java                          (entidade JPA → tabela audit_log)
├── AuditLogRepository.java
└── AuditAspect.java                       (@Aspect que intercepta @Auditable)

test/java/com/dipdv/
├── modules/order/service/
│   └── OrderServiceTest.java
└── shared/audit/
    └── AuditAspectTest.java
```

---

## Tarefa 1 — Entidade Order

**Arquivo:** `modules/order/entity/Order.java`

```java
@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "cash_register_id")
    private UUID cashRegisterId;               // nullable — opcional no MVP

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "order_status")
    @Builder.Default
    private OrderStatus status = OrderStatus.OPEN;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "cancel_reason")
    private String cancelReason;               // obrigatório quando CANCELED

    @Version                                   // Optimistic Locking
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
```

**Enum OrderStatus:** `modules/order/entity/enums/OrderStatus.java`
```java
public enum OrderStatus { OPEN, CLOSED, CANCELED }
```

---

## Tarefa 2 — Entidades OrderItem e OrderItemModifier

**`OrderItem.java`:**
- `id` UUID PK
- `orderId` UUID NOT NULL (sem @ManyToOne — evitar lazy loading acidental)
- `productId` UUID NOT NULL
- `productName` VARCHAR(120) NOT NULL — snapshot do nome do produto
- `quantity` SMALLINT NOT NULL DEFAULT 1 — `@JdbcTypeCode(SMALLINT)`
- `unitPrice` NUMERIC(10,2) NOT NULL — snapshot do preço
- `totalPrice` NUMERIC(10,2) NOT NULL — calculado com modificadores
- `createdAt`

**`OrderItemModifier.java`:**
- `id` UUID PK
- `orderItemId` UUID NOT NULL
- `modifierOptionId` UUID NOT NULL
- `name` VARCHAR(80) NOT NULL — snapshot do nome da opção
- `priceAddition` NUMERIC(10,2) NOT NULL — snapshot do acréscimo
- `quantity` SMALLINT NOT NULL DEFAULT 1 — `@JdbcTypeCode(SMALLINT)`

> ⚠️ Todos os campos de preço e nome são **snapshots imutáveis**.
> Nunca atualizar após o item ser criado.

---

## Tarefa 3 — Repositories

**`OrderRepository.java`:**
```java
// Listar pedidos do tenant com paginação
Page<Order> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

// Buscar por id garantindo tenant
Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId);

// Listar pedidos abertos do tenant
List<Order> findByTenantIdAndStatus(UUID tenantId, OrderStatus status);

// Buscar com lock pessimista para edição (alternativa ao @Version em casos críticos)
@Lock(LockModeType.OPTIMISTIC)
@Query("SELECT o FROM Order o WHERE o.id = :id AND o.tenantId = :tenantId")
Optional<Order> findByIdAndTenantIdWithLock(
    @Param("id") UUID id,
    @Param("tenantId") UUID tenantId
);
```

**`OrderItemRepository.java`:**
```java
// Itens de um pedido com fetch join para os modificadores (1 query)
@Query("""
    SELECT oi FROM OrderItem oi
    LEFT JOIN FETCH oi.modifiers m
    WHERE oi.orderId = :orderId
    ORDER BY oi.createdAt ASC
""")
List<OrderItem> findByOrderIdWithModifiers(@Param("orderId") UUID orderId);
```

> Mapear `List<OrderItemModifier> modifiers` em `OrderItem` com
> `@OneToMany(mappedBy = "orderItemId", fetch = FetchType.LAZY, cascade = ALL, orphanRemoval = true)`

---

## Tarefa 4 — DTOs

**`CreateOrderRequest.java`** — record com:
- `cashRegisterId` UUID — opcional (nullable)

**`AddItemRequest.java`** — record com:
- `productId` UUID — `@NotNull`
- `quantity` Integer — `@Min(1)`, default 1
- `modifiers` `List<ModifierSelectionRequest>` — opcional (vazio para produto simples)

**`ModifierSelectionRequest.java`** — record interno com:
- `modifierOptionId` UUID — `@NotNull`
- `quantity` Integer — `@Min(1)`, default 1

**`OrderResponse.java`** — record com:
`id`, `status`, `total`, `cashRegisterId`, `items` (`List<OrderItemResponse>`),
`version`, `createdAt`, `closedAt`

**`OrderItemResponse.java`** — record com:
`id`, `productId`, `productName`, `quantity`, `unitPrice`, `totalPrice`,
`modifiers` (`List<OrderItemModifierResponse>`)

**`OrderItemModifierResponse.java`** — record com:
`id`, `modifierOptionId`, `name`, `priceAddition`, `quantity`

**`OrderSummaryResponse.java`** — record sem itens para listagem:
`id`, `status`, `total`, `itemCount`, `createdAt`

---

## Tarefa 5 — OrderService

**Arquivo:** `modules/order/service/OrderService.java`

`@Service @RequiredArgsConstructor @Slf4j`

### Métodos obrigatórios

```java
// ── Pedido ─────────────────────────────────────────────────────────────────

@Transactional
public OrderResponse createOrder(CreateOrderRequest request)
// Cria pedido OPEN com total = 0
// userId vem do SecurityContextHolder (DiPdvAuthDetails)
// tenantId vem do TenantContext.getRequired()

@Transactional(readOnly = true)
public OrderResponse getOrder(UUID orderId)
// Busca pedido + itens com modificadores (fetch join)
// 404 se não encontrado no tenant

@Transactional(readOnly = true)
public Page<OrderSummaryResponse> listOrders(OrderStatus status, Pageable pageable)
// Filtra por status se fornecido

@Transactional
public OrderResponse addItem(UUID orderId, AddItemRequest request)
// 1. Buscar pedido — 404 se não encontrado
// 2. Validar status OPEN — 409 se CLOSED ou CANCELED
// 3. Buscar Product — 404 se não encontrado no tenant
// 4. Carregar grupos de modificadores do produto
// 5. Validar seleções de modificadores (ver regras abaixo)
// 6. Criar OrderItem com preços congelados
// 7. Criar OrderItemModifiers com snapshots
// 8. Recalcular Order.total
// 9. Salvar e retornar OrderResponse completo

@Transactional
public OrderResponse removeItem(UUID orderId, UUID itemId)
// Validar status OPEN
// Remover item (cascade remove os modificadores)
// Recalcular Order.total

@Transactional
@Auditable(action = AuditAction.ORDER_CANCELED, entity = "orders")
public OrderResponse cancelOrder(UUID orderId, String reason)
// 1. Validar status OPEN — 409 se já CLOSED ou CANCELED
// 2. reason obrigatório — 400 se vazio/nulo
// 3. Setar status CANCELED + cancelReason + closedAt
// 4. @Auditable intercepta e grava em audit_log automaticamente

@Transactional
public OrderResponse closeOrder(UUID orderId)
// Validar status OPEN
// Setar status CLOSED + closedAt
// NÃO abater estoque aqui — ocorre no PaymentService ao confirmar PAID

// ── Estoque (contrato para PaymentService) ──────────────────────────────────

@Transactional
public void deductStockForOrder(UUID orderId)
// Chamado pelo PaymentService quando Payment.status = PAID
// Para cada OrderItem:
//   Product.stockQuantity -= item.quantity
//   Salvar StockMovement(type=SALE, quantity=-item.quantity, orderId=orderId)
//   Se stockQuantity <= stockMinLevel: logar alerta de estoque baixo
// NÃO lançar exceção se estoque for insuficiente — apenas alertar
// Venda já foi confirmada e paga — não pode ser revertida por falta de estoque
```

### Validação de modificadores (método privado)

```java
private void validateModifierSelections(
        Product product,
        List<AddItemRequest.ModifierSelectionRequest> selections,
        List<ModifierGroup> productGroups) {

    // Para produto simples (sem grupos), selections deve ser vazia
    if (productGroups.isEmpty() && !selections.isEmpty()) {
        throw new BusinessException(
            "Produto não aceita modificadores", HttpStatus.BAD_REQUEST);
    }

    // Para cada grupo vinculado ao produto
    for (ModifierGroup group : productGroups) {
        List<ModifierSelectionRequest> groupSelections = selections.stream()
            .filter(s -> group.getOptions().stream()
                .anyMatch(o -> o.getId().equals(s.modifierOptionId())))
            .toList();

        int totalQty = groupSelections.stream()
            .mapToInt(ModifierSelectionRequest::quantity).sum();

        if (totalQty < group.getMinSelect()) {
            throw new BusinessException(
                "Grupo '" + group.getName() + "' exige ao menos " +
                group.getMinSelect() + " seleção(ões)", HttpStatus.BAD_REQUEST);
        }
        if (totalQty > group.getMaxSelect()) {
            throw new BusinessException(
                "Grupo '" + group.getName() + "' aceita no máximo " +
                group.getMaxSelect() + " seleção(ões)", HttpStatus.BAD_REQUEST);
        }

        // Validar maxQuantity por opção
        for (ModifierSelectionRequest sel : groupSelections) {
            ModifierOption option = group.getOptions().stream()
                .filter(o -> o.getId().equals(sel.modifierOptionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    "Opção de modificador não encontrada", HttpStatus.NOT_FOUND));

            if (sel.quantity() > option.getMaxQuantity()) {
                throw new BusinessException(
                    "Opção '" + option.getName() + "' aceita no máximo " +
                    option.getMaxQuantity() + " unidade(s)", HttpStatus.BAD_REQUEST);
            }
        }
    }
}
```

---

## Tarefa 6 — Camada de Auditoria (shared/audit)

### 6a. AuditAction enum

```java
package com.dipdv.shared.audit;

public enum AuditAction {
    ORDER_CANCELED,
    ORDER_CLOSED,
    CASH_REGISTER_CLOSED,
    STOCK_ADJUSTED,
    PRODUCT_DELETED,
    USER_DEACTIVATED
}
```

### 6b. @Auditable — anotação customizada

```java
package com.dipdv.shared.audit;

import java.lang.annotation.*;

/**
 * Marca métodos de Service para interceptação pelo AuditAspect.
 * O método anotado deve ter o ID da entidade como primeiro parâmetro UUID.
 *
 * Uso:
 *   @Auditable(action = AuditAction.ORDER_CANCELED, entity = "orders")
 *   public OrderResponse cancelOrder(UUID orderId, String reason) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    AuditAction action();
    String entity();
}
```

### 6c. AuditLog entity

```java
package com.dipdv.shared.audit;

@Entity
@Table(name = "audit_log")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;                       // null se ação do sistema

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 60)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

### 6d. AuditAspect — o coração da auditoria

```java
package com.dipdv.shared.audit;

import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Intercepta métodos anotados com @Auditable e grava em audit_log.
 *
 * @AfterReturning garante que só audita operações BEM-SUCEDIDAS.
 * Se o método lançar exceção, o log NÃO é gravado — correto por design.
 *
 * CONVENÇÃO: O primeiro parâmetro UUID do método anotado é o entityId.
 * Exemplo: cancelOrder(UUID orderId, String reason) → entityId = orderId
 *
 * AOP cobre a disciplina de POO: separação de concerns, interceptação
 * declarativa, sem poluir os Services com código de infraestrutura.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void logAudit(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            UUID tenantId = TenantContext.get();
            UUID userId = extractUserId();
            UUID entityId = extractEntityId(joinPoint);

            AuditLog log = AuditLog.builder()
                .tenantId(tenantId)
                .userId(userId)
                .action(auditable.action().name())
                .entity(auditable.entity())
                .entityId(entityId)
                .payload(Map.of(
                    "method", joinPoint.getSignature().getName(),
                    "args", summarizeArgs(joinPoint.getArgs())
                ))
                .build();

            auditLogRepository.save(log);

        } catch (Exception e) {
            // Falha na auditoria nunca deve derrubar a operação principal
            log.error("Falha ao gravar audit_log para {}: {}",
                auditable.action(), e.getMessage());
        }
    }

    private UUID extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof DiPdvAuthDetails details) {
                return details.userId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private UUID extractEntityId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof UUID uuid) return uuid;
        }
        return null;
    }

    private String summarizeArgs(Object[] args) {
        // Nunca logar senhas ou dados sensíveis
        // Logar apenas tipos primitivos e Strings curtas
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof UUID || arg instanceof String str && str.length() < 200) {
                sb.append(arg).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
```

---

## Tarefa 7 — OrderController

```
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Gerenciamento de pedidos no PDV")
```

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/` | CASHIER | Criar pedido |
| GET | `/{id}` | CASHIER | Buscar pedido completo |
| GET | `/` | MANAGER | Listar pedidos (paginado, filtro por status) |
| POST | `/{id}/items` | CASHIER | Adicionar item |
| DELETE | `/{id}/items/{itemId}` | CASHIER | Remover item |
| PATCH | `/{id}/cancel` | MANAGER | Cancelar pedido |
| PATCH | `/{id}/close` | CASHIER | Fechar pedido |

**Tratamento do OptimisticLockException no Controller:**
```java
@ExceptionHandler(OptimisticLockingFailureException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ApiError handleOptimisticLock(OptimisticLockingFailureException ex) {
    return ApiError.of(409, "CONFLICT",
        "Pedido foi modificado por outro operador. Recarregue e tente novamente.");
}
```

Adicionar esse handler no `GlobalExceptionHandler` (não no Controller).

---

## Tarefa 8 — Testes unitários

**`OrderServiceTest.java`** — cenários obrigatórios:

```java
// ── Pedido ──
createOrder_whenValid_shouldReturnOpenOrder()
getOrder_whenNotFound_shouldThrowNotFound()

// ── Itens ──
addItem_whenOrderClosed_shouldThrowConflict()
addItem_whenProductSimple_shouldCreateItemWithoutModifiers()
addItem_whenModifierQuantityExceedsMax_shouldThrowBadRequest()
addItem_whenRequiredGroupNotSelected_shouldThrowBadRequest()
addItem_whenValid_shouldFreezeProductPrice()     // unit_price snapshot
removeItem_whenOrderOpen_shouldRecalculateTotal()

// ── Cancelamento ──
cancelOrder_whenAlreadyCanceled_shouldThrowConflict()
cancelOrder_whenReasonBlank_shouldThrowBadRequest()
cancelOrder_whenValid_shouldSetStatusAndReason()

// ── Estoque ──
deductStockForOrder_shouldCreateStockMovementPerItem()
```

**`AuditAspectTest.java`** — cenários:

```java
// Usar @SpringBootTest(classes = ...) com contexto mínimo
// ou mockar AuditLogRepository e verificar save()

auditAspect_whenMethodSucceeds_shouldSaveAuditLog()
auditAspect_whenMethodThrowsException_shouldNotSaveAuditLog()
auditAspect_whenTenantContextEmpty_shouldNotBreakOperation()
```

---

## Tarefa 9 — Validação

### 9a. Build e testes
```bash
cd backend
.\mvnw.cmd test
```
Esperado: mínimo **33 testes** (21 anteriores + 12 novos).

### 9b. Smoke tests

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001",
       "email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
```

**Criar pedido:**
```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}' | jq .
```
Esperado: 201 com `status: "OPEN"`, `total: 0`, `version: 0`.

**Adicionar item simples (sem modificadores):**
```bash
ORDER_ID="UUID_DO_PEDIDO"
PRODUCT_ID="UUID_DO_PRODUTO_SIMPLES"
curl -s -X POST http://localhost:8080/api/v1/orders/$ORDER_ID/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":"'$PRODUCT_ID'","quantity":1,"modifiers":[]}' | jq .
```
Esperado: item criado com `unitPrice` = preço do produto, `total` do pedido atualizado.

**Adicionar item com modificador (2x Bacon):**
```bash
MODIFIER_OPTION_ID="UUID_DA_OPCAO_BACON"
curl -s -X POST http://localhost:8080/api/v1/orders/$ORDER_ID/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "productId":"'$PRODUCT_ID'",
    "quantity":1,
    "modifiers":[{"modifierOptionId":"'$MODIFIER_OPTION_ID'","quantity":2}]
  }' | jq .
```
Esperado: `totalPrice` = preço do produto + (2 × priceAddition do bacon).

**Cancelar pedido (deve gravar em audit_log):**
```bash
curl -s -X PATCH \
  "http://localhost:8080/api/v1/orders/$ORDER_ID/cancel" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"reason":"Pedido cancelado a pedido do cliente"}' | jq .
```
Esperado: `status: "CANCELED"`.

**Verificar audit_log no banco:**
```bash
docker exec -it dipdv-postgres psql -U dipdv_app -d dipdv_dev \
  -c "SELECT action, entity, entity_id, created_at FROM audit_log ORDER BY created_at DESC LIMIT 5;"
```
Esperado: linha com `action = ORDER_CANCELED`.

**Testar Optimistic Locking:**
```bash
# Criar novo pedido e tentar editar com version errada
NEW_ORDER_ID="UUID_NOVO_PEDIDO"
# Adicionar item normalmente (version vira 1)
# Tentar adicionar outro item enviando version=0 no header ou via segundo cliente simultâneo
# Esperado: 409 CONFLICT
```

---

## Tarefa 10 — Commit

```bash
git add .
git commit -m "feat(order): implementar módulo de pedidos e auditoria AOP

- Entidades Order, OrderItem, OrderItemModifier com preços congelados
- OrderService: criar, adicionar item, remover, cancelar, fechar pedido
- Validação de modificadores: minSelect/maxSelect/maxQuantity
- Optimistic Locking @Version + 409 no GlobalExceptionHandler
- deductStockForOrder() pronto para integração com PaymentService
- AuditAspect @AfterReturning interceptando @Auditable
- AuditLog gravado em audit_log no cancelamento
- 12 novos testes (OrderServiceTest + AuditAspectTest)

Closes #XX (US01.1, US01.3, US01.4, US01.5, US07.1)"

git push origin feature/US01.1-order-module
```

---

## Checklist final

- [ ] `.\mvnw.cmd test` — mínimo 33 testes passando
- [ ] `POST /orders` → 201 com status OPEN e version 0
- [ ] `POST /orders/{id}/items` produto simples → total atualizado
- [ ] `POST /orders/{id}/items` com 2x modificador → totalPrice correto
- [ ] `PATCH /orders/{id}/cancel` → status CANCELED
- [ ] `audit_log` com linha ORDER_CANCELED (evidência via psql)
- [ ] Preço congelado: `unitPrice` não muda se Product.price for alterado depois
- [ ] 409 CONFLICT em edição simultânea (Optimistic Lock)
- [ ] PR aberto `feature/US01.1-order-module` → `develop`

---

## O que NÃO implementar aqui

- `PaymentService` — próximo sprint
- Integração `deductStockForOrder` com Payment — Sprint 2
- `CashRegisterService` — Sprint 2
- Relatórios — Sprint 3
