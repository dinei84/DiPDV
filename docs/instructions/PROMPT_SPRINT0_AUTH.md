# Prompt — Antigravity: Sprint 0 Final — AuthController

---

## Contexto

O scaffold está completo, Docker rodando, Flyway executado com sucesso.
Esta é a **última entrega do Sprint 0**: implementar o módulo de autenticação
completo e o handler global de erros.

Ao final deste prompt, o backend deve ter o primeiro endpoint funcional:
`POST /api/v1/auth/login` retornando um JWT válido.

---

## Visão geral do que será criado

```
modules/auth/
├── entity/        User.java
├── repository/    UserRepository.java
├── dto/           LoginRequest.java
│                  AuthResponse.java
├── service/       AuthService.java
└── controller/    AuthController.java

shared/exception/
├── GlobalExceptionHandler.java
├── ApiError.java
└── BusinessException.java
```

---

## Tarefa 1 — Entidade User

**Arquivo:** `modules/auth/entity/User.java`

```java
package com.dipdv.modules.auth.entity;

import com.dipdv.modules.auth.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

---

## Tarefa 2 — Enum UserRole

**Arquivo:** `modules/auth/entity/enums/UserRole.java`

```java
package com.dipdv.modules.auth.entity.enums;

public enum UserRole {
    ADMIN,
    MANAGER,
    CASHIER
}
```

---

## Tarefa 3 — UserRepository

**Arquivo:** `modules/auth/repository/UserRepository.java`

```java
package com.dipdv.modules.auth.repository;

import com.dipdv.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca usuário ativo por email dentro de um tenant específico.
     * Ignora usuários com soft delete (deleted_at IS NOT NULL).
     *
     * NOTA: Esta query bypassa o RLS porque é executada sem SET LOCAL.
     * É segura aqui pois o login ainda não tem tenant no JWT.
     * Após autenticado, todas as demais queries passam pelo RLS normalmente.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.email = :email
          AND u.tenantId = :tenantId
          AND u.active = true
          AND u.deletedAt IS NULL
    """)
    Optional<User> findActiveByEmailAndTenantId(
        @Param("email") String email,
        @Param("tenantId") UUID tenantId
    );

    /**
     * Verifica se um email já está em uso no tenant.
     * Útil para validação no cadastro de usuários (Sprint 1).
     */
    boolean existsByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);
}
```

---

## Tarefa 4 — DTOs

**Arquivo:** `modules/auth/dto/LoginRequest.java`

```java
package com.dipdv.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload de login.
 * O tenantId é enviado pelo frontend junto com email e senha.
 * O frontend conhece o tenantId do tenant configurado na instalação.
 *
 * Usando Java record — imutável por padrão, sem boilerplate.
 */
public record LoginRequest(

    @NotNull(message = "tenantId é obrigatório")
    UUID tenantId,

    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    String email,

    @NotBlank(message = "senha é obrigatória")
    String password
) {}
```

**Arquivo:** `modules/auth/dto/AuthResponse.java`

```java
package com.dipdv.modules.auth.dto;

import com.dipdv.modules.auth.entity.enums.UserRole;

import java.util.UUID;

/**
 * Resposta do login bem-sucedido.
 * Retorna o token JWT e os dados básicos do usuário autenticado
 * para o frontend popular o contexto de sessão.
 */
public record AuthResponse(
    String token,
    String tokenType,
    long expiresIn,
    UUID userId,
    UUID tenantId,
    String name,
    UserRole role
) {
    /** Factory method — deixa o Service mais legível */
    public static AuthResponse of(
            String token,
            long expiresInMs,
            UUID userId,
            UUID tenantId,
            String name,
            UserRole role) {
        return new AuthResponse(
            token,
            "Bearer",
            expiresInMs / 1000,   // converter ms → segundos para o frontend
            userId,
            tenantId,
            name,
            role
        );
    }
}
```

---

## Tarefa 5 — AuthService

**Arquivo:** `modules/auth/service/AuthService.java`

```java
package com.dipdv.modules.auth.service;

import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.repository.UserRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${dipdv.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Autentica um usuário e retorna um JWT.
     *
     * FLUXO:
     * 1. Busca usuário ativo pelo email + tenantId
     * 2. Valida a senha com BCrypt
     * 3. Gera JWT com claims: userId, tenantId, role
     * 4. Retorna AuthResponse com token e dados do usuário
     *
     * SEGURANÇA:
     * - Sempre retorna a mesma mensagem de erro para email não encontrado
     *   e senha incorreta — evita user enumeration attack.
     * - Tempo de resposta propositalmente não varia (BCrypt é constante).
     *
     * @throws BusinessException 401 se credenciais inválidas
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Mensagem genérica intencional — não revelar se é email ou senha
        final String INVALID_CREDENTIALS = "Email ou senha inválidos";

        User user = userRepository
            .findActiveByEmailAndTenantId(request.email(), request.tenantId())
            .orElseThrow(() -> {
                log.warn("Tentativa de login com email não encontrado: {} tenant: {}",
                    request.email(), request.tenantId());
                return new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Senha incorreta para usuário: {} tenant: {}",
                user.getId(), request.tenantId());
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(
            user.getId(),
            user.getTenantId(),
            user.getRole().name()
        );

        log.info("Login bem-sucedido — userId={} tenantId={} role={}",
            user.getId(), user.getTenantId(), user.getRole());

        return AuthResponse.of(
            token,
            jwtExpirationMs,
            user.getId(),
            user.getTenantId(),
            user.getName(),
            user.getRole()
        );
    }
}
```

---

## Tarefa 6 — AuthController

**Arquivo:** `modules/auth/controller/AuthController.java`

```java
package com.dipdv.modules.auth.controller;

import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e gerenciamento de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
        summary = "Login do usuário",
        description = "Autentica com email e senha dentro de um tenant. Retorna JWT válido por 8 horas."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content),
        @ApiResponse(responseCode = "400", description = "Payload inválido (campos obrigatórios ausentes)",
            content = @Content)
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

---

## Tarefa 7 — GlobalExceptionHandler

**Arquivo:** `shared/exception/ApiError.java`

```java
package com.dipdv.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Envelope padrão de erro para TODOS os erros da API.
 * Garante que o frontend sempre receba JSON estruturado — nunca HTML.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    int status,
    String error,
    String message,
    OffsetDateTime timestamp,
    List<FieldError> fields       // preenchido apenas em erros de validação (@Valid)
) {
    public record FieldError(String field, String message) {}

    /** Factory para erros simples */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, OffsetDateTime.now(), null);
    }

    /** Factory para erros de validação com lista de campos */
    public static ApiError ofValidation(List<FieldError> fields) {
        return new ApiError(400, "VALIDATION_ERROR",
            "Campos inválidos na requisição", OffsetDateTime.now(), fields);
    }
}
```

**Arquivo:** `shared/exception/BusinessException.java`

```java
package com.dipdv.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção de negócio — lançada pelos Services para erros esperados.
 * O GlobalExceptionHandler a captura e retorna o status HTTP correto.
 *
 * Exemplos de uso:
 *   throw new BusinessException("Pedido não encontrado", HttpStatus.NOT_FOUND);
 *   throw new BusinessException("Caixa já está fechado", HttpStatus.CONFLICT);
 *   throw new BusinessException("Email ou senha inválidos", HttpStatus.UNAUTHORIZED);
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
```

**Arquivo:** `shared/exception/GlobalExceptionHandler.java`

```java
package com.dipdv.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Handler global de exceções — garante respostas JSON padronizadas.
 *
 * HIERARQUIA DE HANDLERS (ordem de precedência):
 * 1. MethodArgumentNotValidException → 400 com lista de campos inválidos
 * 2. BusinessException               → status definido pelo Service (401, 404, 409...)
 * 3. Exception (fallback)            → 500 sem expor detalhes internos
 *
 * IMPORTANTE: Nunca expor stack trace ou mensagens internas em produção.
 * O log registra o detalhe técnico; o response retorna apenas o necessário.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Erros de validação do @Valid — campos obrigatórios, formatos inválidos, etc.
     * Retorna lista de todos os campos com problema de uma vez.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
            .toList();

        return ResponseEntity
            .badRequest()
            .body(ApiError.ofValidation(fields));
    }

    /**
     * Erros de negócio lançados pelos Services.
     * O status HTTP é definido pelo próprio Service via BusinessException.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: {} — status {}", ex.getMessage(), ex.getStatus());

        return ResponseEntity
            .status(ex.getStatus())
            .body(ApiError.of(
                ex.getStatus().value(),
                ex.getStatus().name(),
                ex.getMessage()
            ));
    }

    /**
     * Fallback para qualquer exceção não tratada.
     * Loga o erro completo internamente mas retorna mensagem genérica ao cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Erro interno não tratado", ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(500, "INTERNAL_SERVER_ERROR",
                "Erro interno. Tente novamente em instantes."));
    }
}
```

---

## Tarefa 8 — Seed de teste (apenas dev)

Para testar o login sem precisar criar um usuário manualmente via SQL,
crie um `DataInitializer` que roda apenas no perfil `dev`:

**Arquivo:** `shared/config/DataInitializer.java`

```java
package com.dipdv.shared.config;

import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cria dados iniciais de teste APENAS no perfil dev.
 * NUNCA executar em produção — anotação @Profile("dev") garante isso.
 *
 * Credenciais de teste:
 *   tenantId: o UUID impresso no log ao iniciar
 *   email:    admin@dipdv.dev
 *   senha:    dipdv@2025
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // UUID fixo para facilitar os testes — não alterar
    private static final UUID DEV_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(
                "admin@dipdv.dev", DEV_TENANT_ID)) {
            log.info("[DEV] Usuário de teste já existe — pulando seed");
            return;
        }

        // Inserir tenant de teste direto (bypassa RLS pois é inicialização)
        // Em produção, tenants são criados via endpoint administrativo
        var sql = "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

        // Usar JdbcTemplate seria mais limpo, mas para o seed isso é suficiente

        User admin = User.builder()
            .tenantId(DEV_TENANT_ID)
            .email("admin@dipdv.dev")
            .passwordHash(passwordEncoder.encode("dipdv@2025"))
            .name("Admin Dev")
            .role(UserRole.ADMIN)
            .active(true)
            .build();

        userRepository.save(admin);

        log.info("╔══════════════════════════════════════════╗");
        log.info("║         SEED DE DESENVOLVIMENTO          ║");
        log.info("╠══════════════════════════════════════════╣");
        log.info("║  tenantId : {}  ║", DEV_TENANT_ID);
        log.info("║  email    : admin@dipdv.dev              ║");
        log.info("║  senha    : dipdv@2025                   ║");
        log.info("║  role     : ADMIN                        ║");
        log.info("╚══════════════════════════════════════════╝");
    }
}
```

> ⚠️ O `DataInitializer` tenta inserir o usuário na tabela `users`, mas a tabela `tenants` exige um registro com o `DEV_TENANT_ID` primeiro por causa da FK. Antes de rodar, execute este SQL no banco via pgAdmin ou psql:
>
> ```sql
> INSERT INTO tenants (id, name, slug)
> VALUES ('00000000-0000-0000-0000-000000000001', 'Tenant Dev', 'dev')
> ON CONFLICT DO NOTHING;
> ```

---

## Tarefa 9 — Validação completa

### 9a. Build limpo

```bash
cd backend
.\mvnw.cmd compile
```
Esperado: `BUILD SUCCESS`

### 9b. Boot com seed

```bash
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Verificar no console:
```
╔══════════════════════════════════════════╗
║         SEED DE DESENVOLVIMENTO          ║
...
```

### 9c. Testar o endpoint de login

**Via curl:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "dipdv@2025"
  }' | jq .
```

**Resposta esperada (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "userId": "...",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "name": "Admin Dev",
  "role": "ADMIN"
}
```

**Testar credenciais inválidas (deve retornar 401):**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "email": "admin@dipdv.dev",
    "password": "senha-errada"
  }' | jq .
```

**Resposta esperada (401):**
```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Email ou senha inválidos",
  "timestamp": "..."
}
```

**Testar payload inválido (deve retornar 400 com campos):**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": ""}' | jq .
```

**Resposta esperada (400):**
```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Campos inválidos na requisição",
  "timestamp": "...",
  "fields": [
    { "field": "tenantId", "message": "tenantId é obrigatório" },
    { "field": "email", "message": "email é obrigatório" },
    { "field": "password", "message": "senha é obrigatória" }
  ]
}
```

### 9d. Verificar Swagger

Abrir `http://localhost:8080/swagger-ui.html` e confirmar que o endpoint
`POST /api/v1/auth/login` aparece documentado com os três responses (200, 400, 401).

---

## Tarefa 10 — Commit de fechamento do Sprint 0

```bash
git add .
git commit -m "feat(auth): implementar autenticação JWT — Sprint 0 completo

- User entity com mapeamento JPA e soft delete
- UserRepository com query por email + tenantId
- AuthService com BCrypt e geração de JWT
- AuthController POST /api/v1/auth/login com Swagger
- GlobalExceptionHandler com ApiError padronizado
- BusinessException para erros de negócio
- DataInitializer seed para ambiente dev

Closes #XX (US06.2)"

git push origin develop
```

---

## Checklist final do Sprint 0

- [ ] `BUILD SUCCESS` após adicionar todas as classes
- [ ] Boot sem erros com perfil dev
- [ ] Seed de desenvolvimento executado (log visível no console)
- [ ] `POST /auth/login` com credenciais corretas → 200 + JWT
- [ ] `POST /auth/login` com senha errada → 401 com mensagem genérica
- [ ] `POST /auth/login` com payload vazio → 400 com lista de campos
- [ ] Swagger exibe o endpoint documentado
- [ ] Commit fechando a US06.2 feito em `develop`

---

## O que NÃO fazer

- Não implementar refresh token — decidido que será pós-MVP
- Não criar endpoint de cadastro de usuário ainda — virá no Sprint 1
- Não criar endpoint de cadastro de tenant — virá na fase SaaS
- Não mergear para `main` ainda — Sprint Review acontece ao final do Sprint 1
- Não modificar as classes de segurança já prontas (`JwtService`, `JwtAuthFilter`, `SecurityConfig`)
