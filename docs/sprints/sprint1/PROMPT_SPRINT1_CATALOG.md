# Prompt — Antigravity: Sprint 1 — Módulo Catalog (Category + Product)

---

## Contexto

Sprint 0 concluído. O backend tem autenticação JWT funcionando, RLS ativo
e `GlobalExceptionHandler` padronizado.

Este prompt implementa o **módulo Catalog** — base de tudo que o PDV precisa:
sem produtos cadastrados, não existe pedido.

Escopo desta entrega:
- `Category` — CRUD completo
- `Product` — CRUD completo com soft delete e controle de estoque básico
- Testes unitários do `CatalogService`
- Documentação Swagger nos Controllers
- Paginação nos endpoints de listagem

`ModifierGroup` e `ModifierOption` ficam para o próximo prompt.

---

## Regras gerais antes de começar

- Todo código novo vai em `feature/US03.1-catalog-category-product` a partir de `develop`
- Padrão de commits: `feat(catalog): ...`
- Nunca expor entidade JPA diretamente — sempre mapear para DTO/Record
- `@PreAuthorize` em todos os endpoints de escrita
- `BusinessException` para erros de negócio (já existe em `shared/exception/`)
- O `tenant_id` vem sempre do `TenantContext.getRequired()` — nunca do body do request
- Soft delete em `Product`: setar `deletedAt`, nunca `DELETE` físico

---

## Estrutura a criar

```
modules/catalog/
├── entity/
│   ├── Category.java
│   └── Product.java
├── repository/
│   ├── CategoryRepository.java
│   └── ProductRepository.java
├── dto/
│   ├── category/
│   │   ├── CategoryRequest.java
│   │   └── CategoryResponse.java
│   └── product/
│       ├── ProductRequest.java
│       └── ProductResponse.java
├── service/
│   └── CatalogService.java
└── controller/
    ├── CategoryController.java
    └── ProductController.java

test/java/com/dipdv/modules/catalog/
└── service/
    └── CatalogServiceTest.java
```

---

## Tarefa 1 — Entidade Category

**Arquivo:** `modules/catalog/entity/Category.java`

Mapeia a tabela `categories`. Campos obrigatórios:
- `id` UUID PK gerado automaticamente
- `tenantId` UUID NOT NULL (sem FK gerenciada pelo JPA — o RLS garante o isolamento)
- `name` VARCHAR(80) NOT NULL
- `active` BOOLEAN DEFAULT TRUE
- `position` SMALLINT DEFAULT 0 (ordem de exibição no PDV)
- `createdAt` e `updatedAt` com `@CreationTimestamp` / `@UpdateTimestamp`

Anotações necessárias: `@Entity`, `@Table(name = "categories")`, `@Getter`, `@Setter`,
`@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.

Não mapear relacionamento com `Product` na entidade — evitar lazy loading
desnecessário. O join será feito via query no repository quando necessário.

---

## Tarefa 2 — Entidade Product

**Arquivo:** `modules/catalog/entity/Product.java`

Mapeia a tabela `products`. Campos obrigatórios:
- `id` UUID PK
- `tenantId` UUID NOT NULL
- `categoryId` UUID (nullable — `ON DELETE SET NULL` no schema)
- `name` VARCHAR(120) NOT NULL
- `description` TEXT (nullable)
- `price` NUMERIC(10,2) NOT NULL
- `stockQuantity` INTEGER DEFAULT 0
- `stockMinLevel` INTEGER DEFAULT 0
- `active` BOOLEAN DEFAULT TRUE com `@Builder.Default`
- `deletedAt` TIMESTAMPTZ (nullable — soft delete)
- `createdAt` e `updatedAt`

> ⚠️ Lembrar do `@Builder.Default` em `active = true` e `stockQuantity = 0`
> — bug já documentado no `errors_corrected_for_sprint0.md`.

---

## Tarefa 3 — Repositories

**Arquivo:** `modules/catalog/repository/CategoryRepository.java`

Interface `JpaRepository<Category, UUID>` com:

```java
// Listar categorias ativas do tenant com paginação
Page<Category> findByTenantIdAndActiveTrueOrderByPositionAsc(UUID tenantId, Pageable pageable);

// Verificar duplicidade de nome no tenant
boolean existsByTenantIdAndNameAndActiveTrue(String name, UUID tenantId);

// Buscar por id garantindo que pertence ao tenant (RLS já filtra, mas boa prática)
Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);
```

**Arquivo:** `modules/catalog/repository/ProductRepository.java`

Interface `JpaRepository<Product, UUID>` com:

```java
// Listar produtos ativos e não deletados com paginação
Page<Product> findByTenantIdAndActiveTrueAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

// Filtrar por categoria
Page<Product> findByTenantIdAndCategoryIdAndActiveTrueAndDeletedAtIsNull(
    UUID tenantId, UUID categoryId, Pageable pageable);

// Buscar por id (não deletado)
Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

// Verificar duplicidade de nome no tenant
boolean existsByTenantIdAndNameAndDeletedAtIsNull(String name, UUID tenantId);

// Buscar produtos com estoque abaixo do mínimo (para alertas)
@Query("""
    SELECT p FROM Product p
    WHERE p.tenantId = :tenantId
      AND p.active = true
      AND p.deletedAt IS NULL
      AND p.stockQuantity <= p.stockMinLevel
""")
List<Product> findLowStockProducts(@Param("tenantId") UUID tenantId);
```

---

## Tarefa 4 — DTOs

**`CategoryRequest.java`** — record com:
- `name` String — `@NotBlank`, `@Size(max = 80)`
- `position` Integer — `@Min(0)`, valor padrão 0 se nulo
- `active` Boolean — opcional, default true

**`CategoryResponse.java`** — record com:
`id`, `name`, `position`, `active`, `createdAt`

**`ProductRequest.java`** — record com:
- `categoryId` UUID — opcional (nullable)
- `name` String — `@NotBlank`, `@Size(max = 120)`
- `description` String — opcional
- `price` BigDecimal — `@NotNull`, `@DecimalMin("0.00")`
- `stockQuantity` Integer — `@Min(0)`, default 0
- `stockMinLevel` Integer — `@Min(0)`, default 0
- `active` Boolean — opcional, default true

**`ProductResponse.java`** — record com:
`id`, `categoryId`, `name`, `description`, `price`,
`stockQuantity`, `stockMinLevel`, `active`, `createdAt`, `updatedAt`

> Não incluir `tenantId` nos responses — nunca expor contexto interno ao cliente.

---

## Tarefa 5 — CatalogService (estrutura base)

**Arquivo:** `modules/catalog/service/CatalogService.java`

Classe `@Service @RequiredArgsConstructor @Slf4j` com injeção de:
`CategoryRepository`, `ProductRepository`

Implementar os seguintes métodos — lógica completa:

### Métodos de Category

```java
// Listar categorias do tenant com paginação
@Transactional(readOnly = true)
public Page<CategoryResponse> listCategories(Pageable pageable)

// Buscar categoria por id
@Transactional(readOnly = true)
public CategoryResponse getCategoryById(UUID id)

// Criar categoria — validar duplicidade de nome no tenant
@Transactional
public CategoryResponse createCategory(CategoryRequest request)

// Atualizar categoria
@Transactional
public CategoryResponse updateCategory(UUID id, CategoryRequest request)

// Inativar categoria (nunca deletar fisicamente)
@Transactional
public void deactivateCategory(UUID id)
```

### Métodos de Product

```java
// Listar produtos com paginação — filtro opcional por categoryId
@Transactional(readOnly = true)
public Page<ProductResponse> listProducts(UUID categoryId, Pageable pageable)

// Buscar produto por id
@Transactional(readOnly = true)
public ProductResponse getProductById(UUID id)

// Criar produto — validar duplicidade de nome
@Transactional
public ProductResponse createProduct(ProductRequest request)

// Atualizar produto
@Transactional
public ProductResponse updateProduct(UUID id, ProductRequest request)

// Soft delete — setar deletedAt = now()
@Transactional
public void deleteProduct(UUID id)

// Listar produtos com estoque abaixo do mínimo
@Transactional(readOnly = true)
public List<ProductResponse> getLowStockProducts()
```

### Regras de negócio obrigatórias no Service

- `tenantId` sempre via `TenantContext.getRequired()` — nunca parâmetro externo
- Nome duplicado no mesmo tenant → `BusinessException("Já existe uma categoria com este nome", HttpStatus.CONFLICT)`
- Id não encontrado → `BusinessException("Categoria não encontrada", HttpStatus.NOT_FOUND)`
- Mapear `Entity → Response` via método privado `toResponse()` — não usar MapStruct ainda
- Logar criação e deleção com `log.info()`

---

## Tarefa 6 — Controllers (estrutura com Swagger)

### CategoryController

**Arquivo:** `modules/catalog/controller/CategoryController.java`

```
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Gerenciamento de categorias do cardápio")
```

Endpoints:

| Método | Path | Role mínima | Descrição |
|---|---|---|---|
| GET | `/` | CASHIER | Listar categorias (paginado) |
| GET | `/{id}` | CASHIER | Buscar por id |
| POST | `/` | ADMIN | Criar categoria |
| PUT | `/{id}` | ADMIN | Atualizar categoria |
| DELETE | `/{id}` | ADMIN | Inativar categoria |

Cada endpoint deve ter `@Operation(summary = "...")` e `@ApiResponses` com
pelo menos os códigos 200, 400, 401, 403 documentados.

Parâmetros de paginação padrão Spring: `?page=0&size=20&sort=position,asc`

### ProductController

**Arquivo:** `modules/catalog/controller/ProductController.java`

```
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Gerenciamento de produtos do cardápio")
```

Endpoints:

| Método | Path | Role mínima | Query param | Descrição |
|---|---|---|---|---|
| GET | `/` | CASHIER | `?categoryId=` (opcional) | Listar produtos (paginado) |
| GET | `/{id}` | CASHIER | — | Buscar por id |
| POST | `/` | ADMIN | — | Criar produto |
| PUT | `/{id}` | ADMIN | — | Atualizar produto |
| DELETE | `/{id}` | ADMIN | — | Soft delete |
| GET | `/low-stock` | MANAGER | — | Produtos com estoque baixo |

---

## Tarefa 7 — Testes unitários

**Arquivo:** `test/java/com/dipdv/modules/catalog/service/CatalogServiceTest.java`

Classe `@ExtendWith(MockitoExtension.class)` com mocks de:
`CategoryRepository`, `ProductRepository`

Implementar os seguintes cenários — um método de teste por cenário:

### Cenários de Category

```java
// ✅ Sucesso
void createCategory_whenValidRequest_shouldReturnResponse()

// ❌ Nome duplicado → deve lançar BusinessException com status 409
void createCategory_whenNameAlreadyExists_shouldThrowConflict()

// ❌ Id não encontrado → deve lançar BusinessException com status 404
void getCategoryById_whenNotFound_shouldThrowNotFound()
```

### Cenários de Product

```java
// ✅ Sucesso
void createProduct_whenValidRequest_shouldReturnResponse()

// ❌ Nome duplicado → BusinessException 409
void createProduct_whenNameAlreadyExists_shouldThrowConflict()

// ✅ Soft delete — deletedAt deve ser setado, não DELETE físico
void deleteProduct_whenExists_shouldSetDeletedAt()

// ❌ Deletar produto inexistente → BusinessException 404
void deleteProduct_whenNotFound_shouldThrowNotFound()

// ✅ Listar produtos com estoque baixo
void getLowStockProducts_shouldReturnOnlyLowStockItems()
```

### Setup do teste

```java
@Mock CategoryRepository categoryRepository;
@Mock ProductRepository productRepository;
@InjectMocks CatalogService catalogService;

// Mock do TenantContext — usar MockedStatic do Mockito
// try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
//     mocked.when(TenantContext::getRequired).thenReturn(TENANT_ID);
//     ...
// }

private static final UUID TENANT_ID =
    UUID.fromString("00000000-0000-0000-0000-000000000001");
```

> O `TenantContext` é estático — usar `mockStatic()` do Mockito 5
> para mockar o `getRequired()` nos testes.

---

## Tarefa 8 — Validação

### 8a. Build e testes

```bash
cd backend
.\mvnw.cmd test
```

Esperado: todos os testes do `CatalogServiceTest` passando.

```bash
.\mvnw.cmd compile
```

Esperado: `BUILD SUCCESS`

### 8b. Boot e smoke tests

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**Criar uma categoria (POST):**
```bash
curl -s -X POST http://localhost:8080/api/v1/categories \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" \
  -d '{"name": "Lanches", "position": 1}' | jq .
```
Esperado: 201 Created com o objeto da categoria.

**Listar categorias (GET paginado):**
```bash
curl -s "http://localhost:8080/api/v1/categories?page=0&size=10" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" | jq .
```
Esperado: página com a categoria criada.

**Criar um produto (POST):**
```bash
curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" \
  -d '{
    "name": "X-Burguer",
    "price": 18.90,
    "stockQuantity": 50,
    "stockMinLevel": 5
  }' | jq .
```
Esperado: 201 Created com o objeto do produto.

**Testar soft delete (DELETE):**
```bash
curl -s -X DELETE http://localhost:8080/api/v1/products/ID_DO_PRODUTO \
  -H "Authorization: Bearer SEU_TOKEN_AQUI"
```
Esperado: 204 No Content. Verificar no pgAdmin que `deleted_at` foi preenchido
e `id` ainda existe na tabela.

**Testar acesso sem role adequada (403):**
```bash
# Usar token de um CASHIER tentando criar categoria
curl -s -X POST http://localhost:8080/api/v1/categories \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN_CASHIER" \
  -d '{"name": "Bebidas"}' | jq .
```
Esperado: 403 Forbidden com envelope `ApiError`.

**Verificar Swagger:**
Abrir `http://localhost:8080/swagger-ui.html` e confirmar que os grupos
"Categorias" e "Produtos" aparecem com todos os endpoints documentados.

---

## Tarefa 9 — Commit

```bash
git add .
git commit -m "feat(catalog): implementar CRUD de categorias e produtos

- Entidades Category e Product com mapeamento JPA
- Repositories com queries por tenant + soft delete
- CatalogService com regras de negócio e TenantContext
- CategoryController e ProductController com Swagger
- Paginação via Spring Data Pageable
- Soft delete em Product via deletedAt
- Testes unitários: 8 cenários no CatalogServiceTest

Closes #XX (US03.1, US03.2)"

git push origin feature/US03.1-catalog-category-product
```

Abrir Pull Request: `feature/US03.1-catalog-category-product` → `develop`

---

## Checklist final

- [ ] `.\mvnw.cmd test` — todos os 8 testes passando
- [ ] `.\mvnw.cmd compile` — BUILD SUCCESS
- [ ] `POST /categories` com token ADMIN → 201
- [ ] `GET /categories` paginado → 200 com página
- [ ] `POST /products` com token ADMIN → 201
- [ ] `DELETE /products/{id}` → 204 + `deleted_at` preenchido no banco
- [ ] `POST /categories` com token CASHIER → 403
- [ ] Swagger exibe grupos "Categorias" e "Produtos"
- [ ] PR aberto para `develop`

---

## O que NÃO implementar neste prompt

- `ModifierGroup` e `ModifierOption` — próximo prompt
- Upload de imagem para produtos — pós-MVP
- Endpoint de reativação de produto deletado — pode implementar como melhoria
- Módulo de Order — próximo após ModifierGroup
