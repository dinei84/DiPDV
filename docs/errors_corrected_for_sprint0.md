# Bugs Corrigidos — Sprint 0

> **Data:** 2026-03-29
> **Contexto:** Primeira execução do backend DiPDV contra o PostgreSQL rodando via Docker.
> **Ambiente:** Spring Boot 3.3.7 · Java 21 · PostgreSQL 17 (Docker) · Perfil `dev`

---

## Bug #1 — Variável `JWT_SECRET` sem valor padrão

### Sintoma
```
Caused by: java.lang.IllegalArgumentException:
  JWT_SECRET deve ter no mínimo 32 caracteres (256 bits para HMAC-SHA256)
Caused by: org.springframework.boot.web.server.WebServerException:
  Unable to start embedded Tomcat
```

### Causa-raiz
O `application.yml` definia:
```yaml
dipdv:
  jwt:
    secret: ${JWT_SECRET}   # sem valor padrão
```
O Spring resolve expressões `${...}` **antes** de carregar os perfis específicos (como `application-dev.yml`). Como a variável de ambiente `JWT_SECRET` não estava exportada no shell, o Spring injetava uma string vazia no construtor do `JwtService`, que lançava `IllegalArgumentException` ao verificar o tamanho mínimo de 32 caracteres — cascateando para o erro do Tomcat.

### Arquivo afetado
`backend/src/main/resources/application.yml`

### Correção
```diff
- secret: ${JWT_SECRET}
+ secret: ${JWT_SECRET:}
```
O sufixo `:{valor_padrão}` instrui o Spring a usar string vazia como fallback quando a variável não estiver definida. O `application-dev.yml` sobrescreve com o segredo real antes que qualquer bean seja construído.

> Em produção, `JWT_SECRET` **deve** ser definida como variável de ambiente real (mínimo 32 chars). O fallback vazio é seguro apenas no perfil `dev` porque o `application-dev.yml` sempre o sobrescreve.

---

## Bug #2 — CGLIB proxy falha no `TenantFilter`

### Sintoma
```
WARN o.s.aop.framework.CglibAopProxy: Unable to proxy [class TenantFilter]
  because it is marked as final
Caused by: java.lang.IllegalStateException:
  StandardEngine[Tomcat]...TomcatEmbeddedContext[] failed to start
Caused by: org.springframework.boot.web.server.WebServerException:
  Unable to start embedded Tomcat
```

### Causa-raiz
O `TenantFilter` usava `@Transactional` diretamente num método `protected` de `OncePerRequestFilter`:

```java
@Override
@Transactional          // problema aqui
protected void doFilterInternal(...) {
    entityManager.createNativeQuery("SET LOCAL ...").executeUpdate();
}
```

O Spring AOP cria proxies via CGLIB para honrar `@Transactional`. Porém, o Tomcat 10 marca o contexto do servlet como `final` durante a inicialização, impedindo que o CGLIB crie uma subclasse do filtro. O resultado é uma falha de proxy que impede o contexto inteiro de subir.

### Arquivos afetados
- `backend/src/main/java/com/dipdv/shared/tenant/TenantFilter.java`

### Correção
Criado `TenantContextService.java` — a lógica transacional foi extraída para um `@Service` separado:

**Novo arquivo — `TenantContextService.java`:**
```java
@Service
public class TenantContextService {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void applyTenantContext(UUID tenantId) {
        entityManager
            .createNativeQuery("SET LOCAL app.current_tenant = :tenantId")
            .setParameter("tenantId", tenantId.toString())
            .executeUpdate();
    }
}
```

**`TenantFilter.java` refatorado** — delega via injeção de dependência:
```java
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {
    private final TenantContextService tenantContextService;

    @Override
    protected void doFilterInternal(...) {
        UUID tenantId = TenantContext.get();
        if (tenantId != null) {
            tenantContextService.applyTenantContext(tenantId);
        }
        filterChain.doFilter(request, response);
    }
}
```

> Padrão correto: **nunca** colocar `@Transactional` diretamente em filtros Spring — sempre delegar para um `@Service`.

---

## Bug #3 — FK violation no seed de desenvolvimento

### Sintoma
```
Caused by: org.postgresql.util.PSQLException:
  ERROR: insert or update on table "users" violates foreign key constraint
  "users_tenant_id_fkey"
  Detail: Key (tenant_id)=(00000000-0000-0000-0000-000000000001)
  is not present in table "tenants".
```

### Causa-raiz
O `DataInitializer` declarava o SQL de INSERT no tenant mas **nunca o executava** — era dead code:

```java
// Bug: variável declarada, nunca usada
var sql = "INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
// código parava aqui e ia direto para o userRepository.save()
```

### Arquivo afetado
`backend/src/main/java/com/dipdv/shared/config/DataInitializer.java`

### Correção
Implementado o INSERT do tenant usando `JdbcTemplate`, executado **antes** da criação do usuário:

```java
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate; // injetado

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Criar tenant primeiro (ON CONFLICT = idempotente)
        jdbcTemplate.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            DEV_TENANT_ID.toString(), "Lanchonete Dev", "dev-tenant"
        );
        // 2. Só então criar usuário (FK satisfeita)
        userRepository.save(admin);
    }
}
```

---

## Bug #4 — `@Builder` ignora valor padrão do campo `active`

### Sintoma
Aviso do compilador Lombok:
```
@Builder will ignore the initializing expression entirely.
  Add @Builder.Default if you want the default value to be used.
```

### Causa-raiz
O campo `active = true` na entidade `User` não tinha `@Builder.Default`. O Lombok com `@Builder` ignora inicializadores de campo sem essa anotação, fazendo `User.builder().build().isActive()` retornar `false`.

### Arquivo afetado
`backend/src/main/java/com/dipdv/modules/auth/entity/User.java`

### Correção
```diff
+ @Builder.Default
  @Column(nullable = false)
  private boolean active = true;
```

---

## Bug #5 — Tipo ENUM nativo PostgreSQL incompatível com JDBC

### Sintoma
```
Caused by: org.postgresql.util.PSQLException:
  ERROR: column "role" is of type user_role
  but expression is of type character varying
```

### Causa-raiz
O schema PostgreSQL define `role` como ENUM nativo (`CREATE TYPE user_role AS ENUM (...)`). O Hibernate com `@Enumerated(EnumType.STRING)` envia o valor como `character varying` no binding JDBC, gerando incompatibilidade de tipos.

### Arquivo afetado
`backend/src/main/java/com/dipdv/modules/auth/entity/User.java`

### Correção
Adicionadas anotações do Hibernate 6 para mapear corretamente o ENUM nativo:

```diff
  @Enumerated(EnumType.STRING)
+ @JdbcTypeCode(SqlTypes.NAMED_ENUM)
- @Column(nullable = false, length = 20)
+ @Column(nullable = false, length = 20, columnDefinition = "user_role")
  private UserRole role;
```

- `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` → usa o tipo ENUM nativo no binding JDBC
- `columnDefinition = "user_role"` → usa o nome exato do tipo ao gerar DDL

---

## Resultado Final

Após todas as correções, a aplicação inicializa com sucesso:

```
Tomcat started on port 8080 (http) with context path '/'
Started DiPdvApplication in 8.117 seconds (process running for 8.575)
[DEV] SEED DE DESENVOLVIMENTO executado com sucesso
```
