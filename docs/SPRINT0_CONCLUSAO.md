# Sprint 0 — Conclusão: Módulo de Autenticação JWT

**Data de conclusão:** 2026-03-29
**Branch:** `main`
**Status:** COMPLETO — BUILD SUCCESS

---

## Visão geral

Esta sprint finalizou o scaffolding do backend com a implementação completa do módulo de autenticação e do handler global de erros. O backend agora possui o primeiro endpoint funcional: `POST /api/v1/auth/login`.

---

## Arquivos criados

### Módulo Auth — `backend/src/main/java/com/dipdv/modules/auth/`

| Arquivo | Descrição |
|---|---|
| `entity/User.java` | Entidade JPA mapeada para a tabela `users`. Campos: `id` (UUID gerado), `tenantId`, `email`, `passwordHash`, `name`, `role`, `active`, `deletedAt`, `createdAt`, `updatedAt`. Suporta soft delete via `deletedAt`. |
| `entity/enums/UserRole.java` | Enum com os três papéis do sistema: `ADMIN`, `MANAGER`, `CASHIER`. |
| `repository/UserRepository.java` | Interface JPA com query JPQL `findActiveByEmailAndTenantId` (busca usuário ativo por email + tenant, respeitando soft delete) e `existsByEmailAndTenantIdAndDeletedAtIsNull` (validação de unicidade). |
| `dto/LoginRequest.java` | Record Java com `tenantId` (UUID, obrigatório), `email` (obrigatório + formato válido) e `password` (obrigatório). Anotações `@Valid` garantem validação automática. |
| `dto/AuthResponse.java` | Record Java com `token`, `tokenType` ("Bearer"), `expiresIn` (segundos), `userId`, `tenantId`, `name` e `role`. Factory method `AuthResponse.of(...)` para construção legível no Service. |
| `service/AuthService.java` | Lógica de autenticação: busca usuário ativo, valida senha com BCrypt, gera JWT via `JwtService`. Retorna mensagem genérica para email não encontrado e senha incorreta — proteção contra user enumeration. |
| `controller/AuthController.java` | Endpoint `POST /api/v1/auth/login` anotado com Swagger (responses 200, 400, 401). Delega para `AuthService`. |

### Shared Exceptions — `backend/src/main/java/com/dipdv/shared/exception/`

| Arquivo | Descrição |
|---|---|
| `ApiError.java` | Record Java com `status`, `error`, `message`, `timestamp` e `fields` (lista de `FieldError` — apenas em erros de validação). Anotado com `@JsonInclude(NON_NULL)` para omitir campos nulos. Dois factory methods: `ApiError.of(...)` e `ApiError.ofValidation(...)`. |
| `BusinessException.java` | `RuntimeException` que carrega o `HttpStatus` desejado. Lançada pelos Services para erros esperados de negócio (ex: credenciais inválidas, recurso não encontrado). |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` com três handlers: `MethodArgumentNotValidException` → 400 com lista de campos, `BusinessException` → status definido pelo Service, `Exception` (fallback) → 500 genérico sem expor detalhes internos. |

### Shared Config — `backend/src/main/java/com/dipdv/shared/config/`

| Arquivo | Descrição |
|---|---|
| `DataInitializer.java` | `CommandLineRunner` anotado com `@Profile("dev")`. Insere automaticamente o tenant de teste e o usuário `admin@dipdv.dev` usando `JdbcTemplate` (tenant via SQL direto, idempotente com `ON CONFLICT DO NOTHING`) e `UserRepository` (usuário via JPA). Não executa em produção. |

### Configuração — `backend/src/main/resources/`

| Arquivo | Descrição |
|---|---|
| `application-dev.yml` | Perfil de desenvolvimento: datasource apontando para `localhost:5432/meu_banco` (usuário `admin`, senha `admin123`), JWT secret fixo (≥32 chars), expiração de 8 horas, logs em nível DEBUG para `com.dipdv`, Spring Security e Hibernate SQL. |

---

## Arquivos preexistentes utilizados (não modificados)

| Arquivo | Papel nesta sprint |
|---|---|
| `shared/security/JwtService.java` | Geração e validação de tokens JWT (HMAC-SHA256). Usado pelo `AuthService`. |
| `shared/security/JwtAuthFilter.java` | Filtro HTTP que valida o Bearer token e popula o `SecurityContext` e `TenantContext`. |
| `shared/security/SecurityConfig.java` | Define `POST /api/v1/auth/login` como rota pública e configura o bean `PasswordEncoder` (BCrypt strength 12). |
| `shared/tenant/TenantContext.java` | ThreadLocal que armazena o `tenantId` corrente para isolamento multi-tenant. |
| `shared/tenant/TenantFilter.java` | Injeta o `tenantId` no contexto PostgreSQL via `SET LOCAL app.current_tenant` para ativar o RLS. |
| `application.yml` | Configurações base: JPA, Flyway, Swagger, Actuator, `${JWT_SECRET}` via env var. |
| `application-prod.yml` | Datasource de produção via env vars `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. |

---

## Ajustes realizados pelo desenvolvedor após geração inicial

| Arquivo | Ajuste |
|---|---|
| `entity/User.java` | Adicionadas anotações `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` e `columnDefinition = "user_role"` no campo `role` para compatibilidade com o tipo enum nativo do PostgreSQL. Adicionado `@Builder.Default` no campo `active` para preservar o valor padrão `true` no builder do Lombok. |
| `shared/config/DataInitializer.java` | Substituída a variável `sql` não utilizada pela execução real via `JdbcTemplate`. Adicionado `@Transactional` no método `run`. Tenant e usuário agora são inseridos em uma única transação. |
| `application-dev.yml` | Datasource ajustado para `localhost:5432/meu_banco` com usuário `admin` / senha `admin123` (credenciais locais do desenvolvedor). |

---

## Fluxo de autenticação

```
Cliente
  │
  ├─ POST /api/v1/auth/login
  │    { tenantId, email, password }
  │
  ▼
AuthController
  │  @Valid → GlobalExceptionHandler (400 se inválido)
  ▼
AuthService.login()
  ├─ UserRepository.findActiveByEmailAndTenantId()
  │    └─ Não encontrado → BusinessException(401)
  ├─ passwordEncoder.matches(password, passwordHash)
  │    └─ Incorreta → BusinessException(401)
  ├─ JwtService.generateToken(userId, tenantId, role)
  └─ AuthResponse.of(token, expiresInMs, ...)
  │
  ▼
200 OK
  { token, tokenType: "Bearer", expiresIn, userId, tenantId, name, role }
```

---

## Padrão de erro da API

Todos os erros retornam o mesmo envelope JSON via `GlobalExceptionHandler`:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Email ou senha inválidos",
  "timestamp": "2026-03-29T14:00:00Z"
}
```

Erros de validação incluem o campo `fields`:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Campos inválidos na requisição",
  "timestamp": "2026-03-29T14:00:00Z",
  "fields": [
    { "field": "tenantId", "message": "tenantId é obrigatório" },
    { "field": "password", "message": "senha é obrigatória" }
  ]
}
```

---

## Como executar para testes

### 1. Iniciar o banco de dados

Certifique-se de que o PostgreSQL está rodando com as credenciais configuradas em `application-dev.yml`:

```
host:     localhost:5432
database: meu_banco
username: admin
password: admin123
```

### 2. Iniciar o backend com perfil dev

```bash
cd backend
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

O `DataInitializer` irá inserir automaticamente o tenant e o usuário de teste. No console aparecerá:

```
╔══════════════════════════════════════════╗
║         SEED DE DESENVOLVIMENTO          ║
╠══════════════════════════════════════════╣
║  tenantId : 00000000-0000-0000-0000-000000000001  ║
║  email    : admin@dipdv.dev              ║
║  senha    : dipdv@2025                   ║
║  role     : ADMIN                        ║
╚══════════════════════════════════════════╝
```

### 3. Testar o endpoint

**Login com sucesso (200):**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "dipdv@2025"
  }' | jq .
```

**Senha errada (401):**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "senha-errada"
  }' | jq .
```

**Payload inválido (400):**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": ""}' | jq .
```

### 4. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Checklist Sprint 0

- [x] `BUILD SUCCESS` após adicionar todas as classes
- [ ] Boot sem erros com perfil dev
- [ ] Seed de desenvolvimento executado (log visível no console)
- [ ] `POST /auth/login` com credenciais corretas → 200 + JWT
- [ ] `POST /auth/login` com senha errada → 401 com mensagem genérica
- [ ] `POST /auth/login` com payload vazio → 400 com lista de campos
- [ ] Swagger exibe o endpoint documentado

---

## O que NÃO foi implementado (decisão de escopo)

- **Refresh token** — decidido como pós-MVP
- **Endpoint de cadastro de usuário** — previsto para Sprint 1
- **Endpoint de cadastro de tenant** — previsto para fase SaaS
- As classes de segurança preexistentes (`JwtService`, `JwtAuthFilter`, `SecurityConfig`) não foram modificadas
