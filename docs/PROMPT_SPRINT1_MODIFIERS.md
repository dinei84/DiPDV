# Prompt — Antigravity: Sprint 1 — ModifierGroup + ModifierOption

---

## Contexto

Catalog (Category + Product) entregue. Este prompt implementa o sistema de
modificadores — a parte mais complexa do módulo Catalog.

Antes de qualquer código, ler este documento inteiro. Há decisões de design
e armadilhas específicas que precisam ser entendidas antes de começar.

**Branch:** `feature/US03.3-modifier-groups` a partir de `develop`
**Commits:** `feat(catalog): ...`

---

## Decisões de design — ler antes de codar

### 1. Quantidade por opção — dois campos, dois propósitos

```
ModifierOption.maxQuantity  → teto definido pelo admin (ex: máx 3 bacons)
OrderItemModifier.quantity  → o que o cliente pediu (ex: 2 bacons)
```

O backend valida: `quantity <= maxQuantity` e `quantity >= 1`.
O frontend nunca controla isso sozinho — toda validação é no backend.

### 2. Produto simples vs. customizável — implícito

Nenhum campo booleano. A lógica é:
- Produto **sem** `ProductModifierGroups` vinculados → simples → PDV não mostra tela de personalização
- Produto **com** grupos vinculados → customizável → PDV mostra opções

O endpoint `GET /products/{id}/modifiers` retorna lista vazia para produtos simples.
O PDV interpreta lista vazia como "produto simples, seguir direto para o carrinho".

### 3. Isolamento RLS sem tenant_id em ModifierOption

`ModifierOption` **não tem** `tenant_id` direto. O isolamento é herdado via FK:
```
ModifierOption → modifier_group_id → ModifierGroup.tenant_id → RLS
```

O RLS do PostgreSQL cobre isso via policy com subquery (já configurado no V2).
**Mas o Service também precisa validar** — antes de qualquer operação em
`ModifierOption`, verificar que o `ModifierGroup` pai pertence ao tenant atual.
Nunca confiar apenas no RLS para essa cadeia.

```java
// Padrão obrigatório para qualquer operação em ModifierOption
ModifierGroup group = groupRepository
    .findByIdAndTenantId(groupId, TenantContext.getRequired())
    .orElseThrow(() -> new BusinessException("Grupo não encontrado", HttpStatus.NOT_FOUND));
// A partir daqui, qualquer option do group é segura
```

### 4. Performance — Fetch Join obrigatório

O endpoint `GET /products/{id}/modifiers` carrega:
`Product → N grupos → cada grupo → M opções`

Sem fetch join = N+1 queries. Com fetch join = 1 query.

```java
@Query("""
    SELECT mg FROM ModifierGroup mg
    LEFT JOIN FETCH mg.options o
    WHERE mg.id IN (
        SELECT pmg.modifierGroup.id FROM ProductModifierGroup pmg
        WHERE pmg.product.id = :productId
          AND pmg.product.tenantId = :tenantId
    )
    AND mg.active = true
    ORDER BY mg.id
""")
List<ModifierGroup> findByProductIdWithOptions(
    @Param("productId") UUID productId,
    @Param("tenantId") UUID tenantId
);
```

> ⚠️ Não usar `DISTINCT` com `LEFT JOIN FETCH` em listas — causa
> `HibernateQueryException`. Usar `IN` com subquery como acima.

### 5. SMALLINT → @JdbcTypeCode obrigatório

`min_select`, `max_select` em `ModifierGroup` e `position`, `max_quantity`
em `ModifierOption` são `SMALLINT` no banco. Já sabemos do Bug #2 do Sprint 0:

```java
@JdbcTypeCode(java.sql.Types.SMALLINT)
@Column(name = "min_select", nullable = false)
private Integer minSelect;
```

Aplicar em **todos** os campos SMALLINT. Sem isso o `ddl-auto: validate` quebra.

---

## Migration V4 — campos novos

Criar `backend/src/main/resources/db/migration/V4__add_modifier_quantity_fields.sql`:

```sql
-- =============================================================================
-- DiPDV — V4__add_modifier_quantity_fields.sql
-- Adiciona suporte a quantidade por opção de modificador
-- max_quantity: teto definido pelo admin na opção
-- quantity: registrado no OrderItemModifier ao finalizar o pedido
-- =============================================================================

-- Quantidade máxima que pode ser selecionada de uma opção (ex: máx 3 bacons)
ALTER TABLE modifier_options
    ADD COLUMN max_quantity SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_max_quantity_positive CHECK (max_quantity >= 1);

COMMENT ON COLUMN modifier_options.max_quantity IS
    'Teto de seleção por opção. Backend valida: quantity <= max_quantity ao finalizar pedido.';

-- Quantidade efetivamente pedida — registrada no OrderItemModifier
ALTER TABLE order_item_modifiers
    ADD COLUMN quantity SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_oim_quantity_positive CHECK (quantity >= 1);

COMMENT ON COLUMN order_item_modifiers.quantity IS
    'Quantidade selecionada pelo cliente. Validado: quantity <= modifier_options.max_quantity.';

-- Índice para consultas de opções ativas por grupo (frequente no PDV)
CREATE INDEX idx_modifier_options_group_active
    ON modifier_options (modifier_group_id, active, position)
    WHERE active = TRUE;
```

---

## Estrutura a criar

```
modules/catalog/
├── entity/
│   ├── ModifierGroup.java
│   ├── ModifierOption.java
│   └── ProductModifierGroup.java        ← entidade associativa N:N
│       └── embedded/
│           └── ProductModifierGroupId.java  ← chave composta @Embeddable
├── repository/
│   ├── ModifierGroupRepository.java
│   ├── ModifierOptionRepository.java
│   └── ProductModifierGroupRepository.java
├── dto/
│   └── modifier/
│       ├── ModifierGroupRequest.java
│       ├── ModifierGroupResponse.java
│       ├── ModifierOptionRequest.java
│       └── ModifierOptionResponse.java
├── service/
│   └── ModifierService.java             ← service dedicado, separado do CatalogService
└── controller/
    └── ModifierController.java

test/java/com/dipdv/modules/catalog/service/
└── ModifierServiceTest.java
```

---

## Tarefa 1 — Entidade ModifierGroup

**Arquivo:** `modules/catalog/entity/ModifierGroup.java`

Campos:
- `id` UUID PK
- `tenantId` UUID NOT NULL
- `name` VARCHAR(80) NOT NULL
- `minSelect` SMALLINT NOT NULL DEFAULT 0 — `@JdbcTypeCode(SMALLINT)`
- `maxSelect` SMALLINT NOT NULL DEFAULT 1 — `@JdbcTypeCode(SMALLINT)`
- `active` BOOLEAN DEFAULT TRUE — `@Builder.Default`
- `createdAt`, `updatedAt`

Relacionamento com opções:
```java
@OneToMany(mappedBy = "modifierGroup",
           fetch = FetchType.LAZY,
           cascade = CascadeType.ALL,
           orphanRemoval = true)
@OrderBy("position ASC")
private List<ModifierOption> options = new ArrayList<>();
```

> `orphanRemoval = true` garante que opções removidas da lista são deletadas
> automaticamente — sem precisar chamar `optionRepository.delete()`.

---

## Tarefa 2 — Entidade ModifierOption

**Arquivo:** `modules/catalog/entity/ModifierOption.java`

Campos:
- `id` UUID PK
- `modifierGroup` — `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "modifier_group_id")`
- `name` VARCHAR(80) NOT NULL
- `priceAddition` NUMERIC(10,2) DEFAULT 0
- `maxQuantity` SMALLINT DEFAULT 1 — `@JdbcTypeCode(SMALLINT)` — `@Builder.Default`
- `active` BOOLEAN DEFAULT TRUE — `@Builder.Default`
- `position` SMALLINT DEFAULT 0 — `@JdbcTypeCode(SMALLINT)` — `@Builder.Default`
- `createdAt`

> **Sem `tenantId`** — isolamento via `modifierGroup.tenantId` + RLS.
> O Service valida o grupo antes de qualquer operação na opção.

---

## Tarefa 3 — Entidade associativa ProductModifierGroup

**Arquivo:** `modules/catalog/entity/ProductModifierGroup.java`

Chave composta com `@EmbeddedId`:

```java
// ProductModifierGroupId.java
@Embeddable
public class ProductModifierGroupId implements Serializable {
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "modifier_group_id")
    private UUID modifierGroupId;

    // equals() e hashCode() obrigatórios — usar @EqualsAndHashCode do Lombok
}
```

```java
// ProductModifierGroup.java
@Entity
@Table(name = "product_modifier_groups")
public class ProductModifierGroup {

    @EmbeddedId
    private ProductModifierGroupId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("modifierGroupId")
    @JoinColumn(name = "modifier_group_id")
    private ModifierGroup modifierGroup;

    @JdbcTypeCode(java.sql.Types.SMALLINT)
    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;
}
```

---

## Tarefa 4 — Repositories

**ModifierGroupRepository:**
```java
// Listar grupos do tenant com paginação
Page<ModifierGroup> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId, Pageable pageable);

// Buscar por id garantindo tenant
Optional<ModifierGroup> findByIdAndTenantId(UUID id, UUID tenantId);

// Verificar nome duplicado
boolean existsByTenantIdAndNameAndActiveTrue(UUID tenantId, String name);

// Fetch join: carregar grupos de um produto com suas opções em 1 query
@Query("""
    SELECT mg FROM ModifierGroup mg
    LEFT JOIN FETCH mg.options o
    WHERE mg.id IN (
        SELECT pmg.modifierGroup.id FROM ProductModifierGroup pmg
        WHERE pmg.product.id = :productId
          AND pmg.product.tenantId = :tenantId
    )
    AND mg.active = true
    ORDER BY mg.id
""")
List<ModifierGroup> findByProductIdWithOptions(
    @Param("productId") UUID productId,
    @Param("tenantId") UUID tenantId
);
```

**ModifierOptionRepository:**
```java
// Buscar opções ativas de um grupo
List<ModifierOption> findByModifierGroupIdAndActiveTrueOrderByPositionAsc(UUID groupId);

// Buscar por id com grupo pai (para validação de tenant)
@Query("""
    SELECT o FROM ModifierOption o
    JOIN FETCH o.modifierGroup mg
    WHERE o.id = :id
""")
Optional<ModifierOption> findByIdWithGroup(@Param("id") UUID id);
```

**ProductModifierGroupRepository:**
```java
// Verificar se vínculo já existe
boolean existsById(ProductModifierGroupId id);

// Remover vínculo específico
void deleteById(ProductModifierGroupId id);

// Contar grupos vinculados a um produto
long countByIdProductId(UUID productId);
```

---

## Tarefa 5 — DTOs

**`ModifierGroupRequest`** — record com:
- `name` — `@NotBlank`, `@Size(max = 80)`
- `minSelect` — `@Min(0)`, default 0
- `maxSelect` — `@Min(1)`
- `active` — opcional, default true
- `options` — `List<ModifierOptionRequest>` — opcional na criação

**`ModifierOptionRequest`** — record com:
- `name` — `@NotBlank`, `@Size(max = 80)`
- `priceAddition` — `@DecimalMin("0.00")`, default 0
- `maxQuantity` — `@Min(1)`, default 1
- `position` — `@Min(0)`, default 0
- `active` — opcional, default true

**`ModifierGroupResponse`** — record com:
`id`, `name`, `minSelect`, `maxSelect`, `active`, `options` (`List<ModifierOptionResponse>`)

**`ModifierOptionResponse`** — record com:
`id`, `name`, `priceAddition`, `maxQuantity`, `position`, `active`

> Nunca incluir `tenantId` nem `modifierGroupId` interno nos responses.

---

## Tarefa 6 — ModifierService

**Arquivo:** `modules/catalog/service/ModifierService.java`

`@Service @RequiredArgsConstructor @Slf4j`

### Métodos e regras de negócio obrigatórias

```java
// ── Modifier Groups ──────────────────────────────────────────────────────

@Transactional(readOnly = true)
Page<ModifierGroupResponse> listGroups(Pageable pageable)

@Transactional(readOnly = true)
ModifierGroupResponse getGroupById(UUID id)

@Transactional
ModifierGroupResponse createGroup(ModifierGroupRequest request)
// Regra: minSelect <= maxSelect → 400 BAD_REQUEST se violar
// Regra: nome duplicado no tenant → 409 CONFLICT

@Transactional
ModifierGroupResponse updateGroup(UUID id, ModifierGroupRequest request)
// Mesmas regras de validação do create

@Transactional
void deactivateGroup(UUID id)
// Regra: não desativar grupo vinculado a produtos ativos → 409 CONFLICT
// Mensagem: "Grupo está vinculado a X produto(s) ativo(s)"

// ── Modifier Options ─────────────────────────────────────────────────────

@Transactional
ModifierOptionResponse addOption(UUID groupId, ModifierOptionRequest request)
// Regra 1: verificar que groupId pertence ao tenant atual ANTES de tudo
// Regra 2: maxQuantity >= 1

@Transactional
ModifierOptionResponse updateOption(UUID groupId, UUID optionId, ModifierOptionRequest request)
// Regra: verificar que option.modifierGroup.id == groupId E pertence ao tenant

@Transactional
void removeOption(UUID groupId, UUID optionId)
// Regra: verificar cadeia tenant antes de remover
// Regra: grupo deve ter ao menos minSelect opções restantes após remoção
//        → 409 se violar (ex: minSelect=1 e só tem 1 opção)

// ── Vínculos Produto ↔ Grupo ─────────────────────────────────────────────

@Transactional(readOnly = true)
List<ModifierGroupResponse> listProductModifiers(UUID productId)
// Usa findByProductIdWithOptions — 1 query com fetch join
// Retorna lista vazia para produtos simples (sem vínculos)

@Transactional
void linkGroupToProduct(UUID productId, UUID groupId, Integer position)
// Regra: produto deve pertencer ao tenant
// Regra: grupo deve pertencer ao tenant
// Regra: vínculo já existe → 409 CONFLICT

@Transactional
void unlinkGroupFromProduct(UUID productId, UUID groupId)
// Regra: verificar que ambos pertencem ao tenant
```

---

## Tarefa 7 — ModifierController

**Arquivo:** `modules/catalog/controller/ModifierController.java`

```
@RestController
@RequiredArgsConstructor
@Tag(name = "Modificadores", description = "Grupos e opções de personalização de produtos")
```

Endpoints:

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/api/v1/modifier-groups` | CASHIER | Listar grupos (paginado) |
| GET | `/api/v1/modifier-groups/{id}` | CASHIER | Buscar grupo |
| POST | `/api/v1/modifier-groups` | ADMIN | Criar grupo |
| PUT | `/api/v1/modifier-groups/{id}` | ADMIN | Atualizar grupo |
| DELETE | `/api/v1/modifier-groups/{id}` | ADMIN | Inativar grupo |
| POST | `/api/v1/modifier-groups/{id}/options` | ADMIN | Adicionar opção |
| PUT | `/api/v1/modifier-groups/{groupId}/options/{optionId}` | ADMIN | Atualizar opção |
| DELETE | `/api/v1/modifier-groups/{groupId}/options/{optionId}` | ADMIN | Remover opção |
| GET | `/api/v1/products/{productId}/modifiers` | CASHIER | Listar modificadores do produto |
| POST | `/api/v1/products/{productId}/modifiers/{groupId}` | ADMIN | Vincular grupo a produto |
| DELETE | `/api/v1/products/{productId}/modifiers/{groupId}` | ADMIN | Desvincular grupo |

Cada endpoint com `@Operation(summary = "...")` e `@ApiResponses` (200/201, 400, 401, 403, 404, 409).

---

## Tarefa 8 — Testes unitários

**Arquivo:** `test/java/com/dipdv/modules/catalog/service/ModifierServiceTest.java`

Cenários obrigatórios:

```java
// ── Groups ──
createGroup_whenValidRequest_shouldReturnResponse()
createGroup_whenNameAlreadyExists_shouldThrowConflict()
createGroup_whenMinSelectGreaterThanMaxSelect_shouldThrowBadRequest()
deactivateGroup_whenLinkedToActiveProducts_shouldThrowConflict()
getGroupById_whenNotFound_shouldThrowNotFound()

// ── Options ──
addOption_whenGroupNotBelongsToTenant_shouldThrowNotFound()
addOption_whenValidRequest_shouldReturnResponse()
removeOption_whenWouldViolateMinSelect_shouldThrowConflict()

// ── Links ──
linkGroupToProduct_whenAlreadyLinked_shouldThrowConflict()
listProductModifiers_whenProductHasNoGroups_shouldReturnEmptyList()
```

Setup com `MockedStatic<TenantContext>` — mesmo padrão do `CatalogServiceTest`.

---

## Tarefa 9 — Validação

### 9a. Build e testes

```bash
cd backend
.\mvnw.cmd test
.\mvnw.cmd compile
```

### 9b. Smoke tests

Obter token ADMIN primeiro:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"00000000-0000-0000-0000-000000000001","email":"admin@dipdv.dev","password":"dipdv@2025"}' \
  | jq -r .token)
```

**Criar grupo de modificadores:**
```bash
curl -s -X POST http://localhost:8080/api/v1/modifier-groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Ponto da carne",
    "minSelect": 1,
    "maxSelect": 1
  }' | jq .
```
Esperado: 201 com o grupo criado.

**Adicionar opções ao grupo:**
```bash
GROUP_ID="UUID_DO_GRUPO_CRIADO"
curl -s -X POST http://localhost:8080/api/v1/modifier-groups/$GROUP_ID/options \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name": "Ao ponto", "priceAddition": 0, "maxQuantity": 1}' | jq .

curl -s -X POST http://localhost:8080/api/v1/modifier-groups/$GROUP_ID/options \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name": "Bacon extra", "priceAddition": 3.50, "maxQuantity": 3}' | jq .
```

**Vincular grupo a produto:**
```bash
PRODUCT_ID="UUID_DO_PRODUTO_XBURGUER"
curl -s -X POST \
  "http://localhost:8080/api/v1/products/$PRODUCT_ID/modifiers/$GROUP_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: 200 ou 204.

**Listar modificadores do produto (fetch join — 1 query):**
```bash
curl -s "http://localhost:8080/api/v1/products/$PRODUCT_ID/modifiers" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: lista com o grupo e suas opções em uma resposta.
Verificar no log que foi executada **apenas 1 query SQL** (com `show-sql: true`).

**Produto simples (sem grupos) deve retornar lista vazia:**
```bash
SIMPLE_PRODUCT_ID="UUID_DE_PRODUTO_SEM_GRUPOS"
curl -s "http://localhost:8080/api/v1/products/$SIMPLE_PRODUCT_ID/modifiers" \
  -H "Authorization: Bearer $TOKEN" | jq .
```
Esperado: `[]`

**Testar validação minSelect > maxSelect (deve retornar 400):**
```bash
curl -s -X POST http://localhost:8080/api/v1/modifier-groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name": "Grupo inválido", "minSelect": 3, "maxSelect": 1}' | jq .
```
Esperado: 400 com mensagem de erro.

---

## Tarefa 10 — Commit

```bash
git add .
git commit -m "feat(catalog): implementar ModifierGroup e ModifierOption

- Entidades ModifierGroup, ModifierOption e ProductModifierGroup
- Migration V4: max_quantity em modifier_options e quantity em order_item_modifiers
- ModifierService com validações de tenant, minSelect/maxSelect e vínculos
- ModifierController com 11 endpoints e Swagger
- Fetch join em listProductModifiers (1 query para produto + grupos + opções)
- Testes unitários: 10 cenários no ModifierServiceTest

Closes #XX (US03.3)"

git push origin feature/US03.3-modifier-groups
```

PR: `feature/US03.3-modifier-groups` → `develop`

---

## Checklist final

- [ ] `.\mvnw.cmd test` — todos os testes passando
- [ ] Migration V4 executada sem erro no boot
- [ ] `POST /modifier-groups` → 201
- [ ] `POST /modifier-groups/{id}/options` → 201
- [ ] `POST /products/{id}/modifiers/{groupId}` → vínculo criado
- [ ] `GET /products/{id}/modifiers` → grupos + opções em 1 query (verificar log SQL)
- [ ] `GET /products/{simpleId}/modifiers` → `[]` para produto sem grupos
- [ ] `minSelect > maxSelect` → 400
- [ ] PR aberto para `develop`

---

## O que NÃO implementar aqui

- Validação de `quantity <= maxQuantity` — será feita no módulo Order ao finalizar pedido
- Lógica de preço total com modificadores — pertence ao OrderService
- Imagem por opção — pós-MVP
