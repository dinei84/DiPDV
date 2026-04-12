# Arquitetura do DiPDV

> Documento de decisões arquiteturais. Atualizar sempre que uma decisão relevante for tomada ou revisada.

---

## Visão Geral

O DiPDV é um **monolito modular** com API REST, frontend PWA e banco PostgreSQL. A arquitetura foi escolhida para equilibrar simplicidade de operação (time pequeno, MVP) com a capacidade de evoluir para microsserviços no futuro, se necessário.

```
┌─────────────────────────────────────────────┐
│              Cliente (Browser/PWA)           │
│              Next.js 14 + Tailwind           │
└──────────────────────┬──────────────────────┘
                       │ HTTPS / REST + JSON
┌──────────────────────▼──────────────────────┐
│           Spring Boot 3 — Monolito Modular   │
│                                             │
│  ┌─────────┐ ┌─────────┐ ┌──────────────┐  │
│  │  auth   │ │ catalog │ │    order     │  │
│  └─────────┘ └─────────┘ └──────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐   │
│  │ payment  │ │inventory │ │  report   │   │
│  └──────────┘ └──────────┘ └───────────┘   │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │  shared: audit / security / tenant  │    │
│  └─────────────────────────────────────┘    │
└──────────────────────┬──────────────────────┘
                       │ JDBC (HikariCP)
┌──────────────────────▼──────────────────────┐
│         PostgreSQL 16 — Shared Schema        │
│         Row Level Security (RLS)             │
│         Flyway Migrations                    │
└─────────────────────────────────────────────┘
```

---

## Decisões Arquiteturais

### ADR-001 — Monolito Modular em vez de Microsserviços

**Contexto:** Time pequeno, MVP, prazo curto.

**Decisão:** Monolito modular com separação por pacotes Java (`com.dipdv.modules.*`). Cada módulo tem seu próprio `Controller`, `Service`, `Repository` e entidades — sem dependências cruzadas entre módulos de negócio.

**Consequências:** Operação simples (um processo, um deploy), menor overhead de rede, mais fácil para aprender. Custo: escalar módulos individualmente não é possível no curto prazo.

**Revisão prevista:** Após atingir 50+ tenants ativos, avaliar extração de módulos críticos (order, payment) como serviços independentes.

---

### ADR-002 — Multi-tenancy via Shared Schema + RLS

**Contexto:** Precisamos isolar dados entre tenants sem complexidade operacional de múltiplos bancos.

**Decisão:** Todas as tabelas de negócio possuem `tenant_id UUID NOT NULL`. O PostgreSQL RLS filtra automaticamente as linhas com base em `current_setting('app.current_tenant')`, injetado pelo `TenantFilter` a cada transação.

**Consequências:** Isolamento garantido no nível do banco (não apenas da aplicação). Migrations únicas para todos os tenants. Risco: uma query sem `SET LOCAL app.current_tenant` retorna dados vazios — nunca dados de outro tenant.

**Alternativas descartadas:** Schema por tenant (overhead de migrations N vezes), banco por tenant (custo inviável no MVP).

---

### ADR-003 — Optimistic Locking em Orders

**Contexto:** Dois operadores podem tentar editar o mesmo pedido simultaneamente.

**Decisão:** Campo `version INTEGER` na entidade `Order`, gerenciado pelo JPA via `@Version`. Conflito gera `OptimisticLockException` → tratado como HTTP 409. Frontend exibe mensagem clara ao operador.

**Consequências:** Sem bloqueio de banco (melhor performance). Caso raro de conflito resulta em retry pelo operador, não em dados corrompidos.

---

### ADR-004 — Idempotência em Payments

**Contexto:** Retentativas de Pix/TEF podem gerar cobranças duplicadas.

**Decisão:** Frontend gera um `idempotency_key` (UUID v4) antes de enviar o request de pagamento. Backend verifica se a chave já existe antes de processar — se sim, retorna o resultado original sem reprocessar.

**Consequências:** Segurança contra duplicidade sem estado adicional no servidor. Chave expira junto com o pagamento (nunca reutilizar para outro pedido).

---

### ADR-005 — Auditoria via AOP (@Aspect)

**Contexto:** Precisamos logar ações críticas sem poluir os Services com código de auditoria.

**Decisão:** `AuditAspect` intercepta métodos anotados com `@Auditable` nos Services. Grava na tabela `audit_log` com snapshot `{before, after}` em JSONB.

**Consequências:** Services permanecem limpos. Auditoria é adicionada declarativamente via anotação. Cobre a disciplina de POO (AOP é um padrão de programação orientada a aspectos).

---

## Arquitetura em Camadas (por módulo)

```
HTTP Request
     │
     ▼
┌──────────────┐
│  Controller  │  Recebe request, valida DTO, delega ao Service
│  (@RestController)  Nunca contém lógica de negócio
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Service    │  Lógica de negócio, transações (@Transactional)
│              │  Chama outros Services do mesmo módulo se necessário
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Repository  │  Acesso ao banco via Spring Data JPA
│  (interface) │  Queries customizadas com @Query quando necessário
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Entity     │  Mapeamento JPA da tabela PostgreSQL
│  (@Entity)   │  Nunca exposta diretamente na API — usar DTOs
└──────────────┘
```

**Regras de dependência entre camadas:**
- Controller → Service → Repository → Entity
- Controller **nunca** acessa Repository diretamente
- Entity **nunca** é retornada em endpoints da API (sempre mapear para DTO/Record)
- Service de um módulo **não** importa Repository de outro módulo

---

## Estrutura de Pacotes

```
com.dipdv/
├── DiPdvApplication.java
│
├── modules/
│   ├── auth/
│   │   ├── controller/     AuthController.java
│   │   ├── service/        AuthService.java
│   │   ├── repository/     UserRepository.java
│   │   ├── entity/         User.java
│   │   └── dto/            LoginRequest.java, TokenResponse.java
│   │
│   ├── catalog/
│   │   ├── controller/     ProductController.java, CategoryController.java
│   │   ├── service/        ProductService.java, CategoryService.java
│   │   ├── repository/     ProductRepository.java, CategoryRepository.java
│   │   ├── entity/         Product.java, Category.java, ModifierGroup.java
│   │   └── dto/
│   │
│   ├── order/
│   │   ├── controller/     OrderController.java
│   │   ├── service/        OrderService.java
│   │   ├── repository/     OrderRepository.java, OrderItemRepository.java
│   │   ├── entity/         Order.java, OrderItem.java, OrderItemModifier.java
│   │   └── dto/
│   │
│   ├── payment/
│   │   ├── controller/     PaymentController.java
│   │   ├── service/        PaymentService.java
│   │   ├── repository/     PaymentRepository.java
│   │   ├── entity/         Payment.java
│   │   └── dto/
│   │
│   ├── cashregister/
│   │   ├── controller/     CashRegisterController.java
│   │   ├── service/        CashRegisterService.java
│   │   ├── repository/     CashRegisterRepository.java
│   │   ├── entity/         CashRegister.java, CashMovement.java
│   │   └── dto/
│   │
│   ├── inventory/
│   │   ├── controller/     StockController.java
│   │   ├── service/        StockService.java
│   │   ├── repository/     StockMovementRepository.java
│   │   ├── entity/         StockMovement.java
│   │   └── dto/
│   │
│   └── report/
│       ├── controller/     ReportController.java
│       ├── service/        ReportService.java
│       └── dto/            SummaryResponse.java, TopProductResponse.java
│
└── shared/
    ├── audit/
    │   ├── AuditAspect.java        @Aspect que intercepta @Auditable
    │   ├── AuditLog.java           Entidade audit_log
    │   ├── AuditLogRepository.java
    │   └── Auditable.java          @interface (anotação customizada)
    │
    ├── security/
    │   ├── JwtService.java         Geração e validação de tokens
    │   ├── JwtAuthFilter.java      Filter que valida JWT em cada request
    │   ├── SecurityConfig.java     Configuração do Spring Security
    │   └── UserDetailsServiceImpl.java
    │
    └── tenant/
        ├── TenantContext.java      ThreadLocal com o tenant atual
        ├── TenantFilter.java       Filter que injeta SET LOCAL app.current_tenant
        └── TenantInterceptor.java  Interceptor para logging
```

---

## Padrões de Código

### Nomenclatura de Endpoints

```
GET    /api/v1/{resource}           → listar (com paginação)
GET    /api/v1/{resource}/{id}      → buscar por id
POST   /api/v1/{resource}           → criar
PUT    /api/v1/{resource}/{id}      → atualizar completo
PATCH  /api/v1/{resource}/{id}      → atualizar parcial / ações (ex: /cancel)
DELETE /api/v1/{resource}/{id}      → remover (soft delete onde aplicável)
```

### Padrão de Response

```json
{
  "data": { ... },
  "message": "Operação realizada com sucesso",
  "timestamp": "2025-03-01T10:00:00Z"
}
```

Erros:
```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Pedido não encontrado",
  "timestamp": "2025-03-01T10:00:00Z",
  "status": 404
}
```

### Convenções de DTOs

- Usar Java `record` para DTOs imutáveis (request/response)
- Sufixo `Request` para entrada: `CreateOrderRequest`, `LoginRequest`
- Sufixo `Response` para saída: `OrderResponse`, `TokenResponse`
- Nunca expor a entidade JPA diretamente na API

### Transações

- `@Transactional` apenas na camada Service
- Métodos de leitura: `@Transactional(readOnly = true)`
- O `TenantFilter` executa `SET LOCAL` dentro da mesma transação

---

## Segurança

| Mecanismo | Implementação |
|---|---|
| Autenticação | JWT + Refresh Token (Spring Security) |
| Autorização | `@PreAuthorize` com roles: ADMIN, MANAGER, CASHIER |
| Isolamento de dados | RLS no PostgreSQL via `app.current_tenant` |
| Senhas | BCrypt (fator 12) |
| Transporte | HTTPS obrigatório em produção |
| CORS | Configurado para domínio do frontend |

---

## Ambientes

| Ambiente | Branch | URL | Banco |
|---|---|---|---|
| `dev` | `feature/*` | localhost:8080 | Docker local |
| `staging` | `develop` | staging.dipdv.app | Railway (staging) |
| `prod` | `main` | api.dipdv.app | Railway (prod) |
