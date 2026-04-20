# T1 — Testes RLS + Testcontainers (Claude Code CLI)

## Instruções para o Claude Code

Você tem acesso ao filesystem do projeto DiPDV. Leia os arquivos necessários
antes de implementar. Este prompt cria a infraestrutura de testes de integração
com Testcontainers e os primeiros testes críticos de isolamento RLS.

**Antes de começar:**
1. Ler `backend/pom.xml` para ver as dependências atuais
2. Ler `backend/src/main/resources/db/migration/V2__rls_policies.sql` para
   entender as policies existentes
3. Verificar se existe `backend/src/test/resources/application-test.yml`
4. Confirmar branch atual: `git branch --show-current`

---

## Contexto do projeto

- Spring Boot 3.3.x + Java 21 + PostgreSQL 16
- Flyway com migrations V1..V8 em `backend/src/main/resources/db/migration/`
- RLS ativo em todas as tabelas com `tenant_isolation` policy
- Kill Switch SUPER_ADMIN: requer `app.is_super_admin = 'true'` E
  `app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'` simultaneamente
- Package base: `com.dipdv`
- Testes unitários existentes em `backend/src/test/java/com/dipdv/`

---

## Tarefa 1 — Adicionar dependências no pom.xml

Localizar a seção `<dependencies>` em `backend/pom.xml` e adicionar:

```xml
<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

Localizar o plugin `maven-surefire-plugin` (ou criar se não existir) e
adicionar configuração de tags:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>${exclude.integration.tests}</excludedGroups>
    </configuration>
</plugin>
```

Adicionar na seção `<properties>`:
```xml
<exclude.integration.tests>integration</exclude.integration.tests>
```

---

## Tarefa 2 — Criar application-test.yml

**Arquivo:** `backend/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_test
    username: dipdv_app
    password: dipdv_test
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration

logging:
  level:
    com.dipdv: WARN
    org.flywaydb: INFO
    org.testcontainers: WARN

dipdv:
  jwt:
    secret: test-secret-key-minimo-256-bits-para-hmac-sha256-aqui-test
    expiration-ms: 3600000
```

---

## Tarefa 3 — Criar anotações customizadas

**Arquivo:** `backend/src/test/java/com/dipdv/support/IntegrationTest.java`

```java
package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
```

**Arquivo:** `backend/src/test/java/com/dipdv/support/RlsTest.java`

```java
package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Testes que validam isolamento de dados entre tenants via RLS.
 * Requerem PostgreSQL real — usam Testcontainers.
 *
 * Todo @RlsTest deve:
 * 1. Criar dados em pelo menos 2 tenants distintos
 * 2. Ativar contexto de um tenant via SET LOCAL
 * 3. Verificar que apenas dados daquele tenant são visíveis
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("rls")
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public @interface RlsTest {
}
```

---

## Tarefa 4 — Criar suporte base Testcontainers

**Arquivo:** `backend/src/test/java/com/dipdv/support/PostgresIntegrationSupport.java`

```java
package com.dipdv.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Classe base para testes com PostgreSQL real via Testcontainers.
 *
 * O container é estático — compartilhado entre todos os testes
 * da classe que herdar este suporte.
 * Flyway aplica V1..V8 automaticamente ao subir o container.
 */
@Testcontainers
public abstract class PostgresIntegrationSupport {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("dipdv_test")
            .withUsername("dipdv_app")
            .withPassword("dipdv_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
```

---

## Tarefa 5 — Criar RlsTestHelper

**Arquivo:** `backend/src/test/java/com/dipdv/support/RlsTestHelper.java`

```java
package com.dipdv.support;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ativa contexto de tenant nos testes sem passar pelo JwtAuthFilter.
 *
 * Uso:
 *   rlsHelper.runAsTenant(TENANT_A, () -> {
 *       var orders = orderRepository.findAll();
 *       assertThat(orders).allMatch(o -> o.getTenantId().equals(TENANT_A));
 *   });
 */
@Component
public class RlsTestHelper {

    private final EntityManager entityManager;

    public RlsTestHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void runAsTenant(UUID tenantId, Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantId + "'"
        ).executeUpdate();
        action.run();
    }

    @Transactional
    public void runAsSuperAdmin(Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.is_super_admin = 'true'; " +
            "SET LOCAL app.current_tenant = " +
            "'ffffffff-ffff-ffff-ffff-ffffffffffff'"
        ).executeUpdate();
        action.run();
    }
}
```

---

## Tarefa 6 — Criar TenantIsolationRlsTest

**Arquivo:** `backend/src/test/java/com/dipdv/rls/TenantIsolationRlsTest.java`

> Antes de criar, ler as entidades existentes para usar os campos corretos:
> - `backend/src/main/java/com/dipdv/modules/catalog/entity/Category.java`
> - `backend/src/main/java/com/dipdv/modules/order/entity/Order.java`
> - `backend/src/main/java/com/dipdv/shared/audit/AuditLog.java`

```java
package com.dipdv.rls;

import com.dipdv.modules.catalog.entity.Category;
import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.modules.order.repository.OrderRepository;
import com.dipdv.shared.audit.AuditLogRepository;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.support.RlsTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RlsTest
class TenantIsolationRlsTest extends PostgresIntegrationSupport {

    static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID USER_A   = UUID.randomUUID();
    static final UUID USER_B   = UUID.randomUUID();

    @Autowired CategoryRepository categoryRepository;
    @Autowired OrderRepository     orderRepository;
    @Autowired AuditLogRepository  auditLogRepository;
    @Autowired RlsTestHelper       rlsHelper;
    @Autowired JdbcTemplate        jdbc;

    @BeforeEach
    void setUp() {
        // Limpar dados de testes anteriores (superadmin bypassa RLS)
        jdbc.execute("SET LOCAL app.is_super_admin = 'true'");
        jdbc.execute("SET LOCAL app.current_tenant = " +
            "'ffffffff-ffff-ffff-ffff-ffffffffffff'");
        jdbc.update("DELETE FROM audit_log WHERE tenant_id IN (?::uuid, ?::uuid)",
            TENANT_A.toString(), TENANT_B.toString());
        jdbc.update("DELETE FROM orders WHERE tenant_id IN (?::uuid, ?::uuid)",
            TENANT_A.toString(), TENANT_B.toString());
        jdbc.update("DELETE FROM categories WHERE tenant_id IN (?::uuid, ?::uuid)",
            TENANT_A.toString(), TENANT_B.toString());
        jdbc.update("DELETE FROM users WHERE tenant_id IN (?::uuid, ?::uuid)",
            TENANT_A.toString(), TENANT_B.toString());

        // Criar tenants de teste
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT (id) DO NOTHING",
            TENANT_A.toString(), "Tenant A", "tenant-a-test");
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT (id) DO NOTHING",
            TENANT_B.toString(), "Tenant B", "tenant-b-test");

        // Criar usuários
        String pwHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCkJ5yI0m.6kgJJ9q2Y5Jmi";
        jdbc.update(
            "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role) " +
            "ON CONFLICT DO NOTHING",
            USER_A.toString(), TENANT_A.toString(), "a@rls-test.com", pwHash, "User A");
        jdbc.update(
            "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role) " +
            "ON CONFLICT DO NOTHING",
            USER_B.toString(), TENANT_B.toString(), "b@rls-test.com", pwHash, "User B");

        // Criar uma categoria por tenant usando SET LOCAL
        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "'");
        jdbc.update(
            "INSERT INTO categories (id, tenant_id, name) VALUES " +
            "(gen_random_uuid(), ?::uuid, 'Categoria Tenant A')",
            TENANT_A.toString());

        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_B + "'");
        jdbc.update(
            "INSERT INTO categories (id, tenant_id, name) VALUES " +
            "(gen_random_uuid(), ?::uuid, 'Categoria Tenant B')",
            TENANT_B.toString());
    }

    @Test
    @DisplayName("Tenant A só vê suas próprias categorias")
    void tenantA_shouldOnlySeeOwnCategories() {
        rlsHelper.runAsTenant(TENANT_A, () -> {
            List<Category> cats = categoryRepository.findAll();
            assertThat(cats).isNotEmpty();
            assertThat(cats).allSatisfy(c ->
                assertThat(c.getTenantId()).isEqualTo(TENANT_A));
        });
    }

    @Test
    @DisplayName("Tenant B não vê categorias do Tenant A")
    void tenantB_shouldNotSeeTenantAData() {
        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<Category> cats = categoryRepository.findAll();
            assertThat(cats).noneMatch(c ->
                c.getTenantId().equals(TENANT_A));
        });
    }

    @Test
    @DisplayName("Pedido do Tenant A é invisível para Tenant B")
    void orderFromTenantA_shouldBeInvisibleToTenantB() {
        UUID orderId = UUID.randomUUID();
        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "'");
        jdbc.update(
            "INSERT INTO orders " +
            "(id, tenant_id, user_id, status, total, version) " +
            "VALUES (?::uuid, ?::uuid, ?::uuid, " +
            "'OPEN'::order_status, 0, 0)",
            orderId.toString(), TENANT_A.toString(), USER_A.toString());

        rlsHelper.runAsTenant(TENANT_B, () -> {
            var orders = orderRepository.findAll();
            assertThat(orders).noneMatch(o -> o.getId().equals(orderId));
        });

        rlsHelper.runAsTenant(TENANT_A, () -> {
            var orders = orderRepository.findAll();
            assertThat(orders).anyMatch(o -> o.getId().equals(orderId));
        });
    }

    @Test
    @DisplayName("SUPER_ADMIN vê dados de todos os tenants")
    void superAdmin_shouldSeeAllTenantsData() {
        rlsHelper.runAsSuperAdmin(() -> {
            List<Category> cats = categoryRepository.findAll();
            assertThat(cats)
                .extracting(Category::getTenantId)
                .contains(TENANT_A, TENANT_B);
        });
    }

    @Test
    @DisplayName("Sem contexto definido, nenhum dado é visível")
    void noContext_shouldSeeNoData() {
        // Sem SET LOCAL, current_setting retorna null → policy false
        List<Category> cats = categoryRepository.findAll();
        assertThat(cats)
            .noneMatch(c -> c.getTenantId().equals(TENANT_A)
                         || c.getTenantId().equals(TENANT_B));
    }
}
```

---

## Tarefa 7 — Criar SuperAdminRlsTest

**Arquivo:** `backend/src/test/java/com/dipdv/rls/SuperAdminRlsTest.java`

```java
package com.dipdv.rls;

import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.support.RlsTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o Kill Switch duplo do RLS:
 * bypass exige is_super_admin=true AND current_tenant=masterUUID.
 * Cada condição sozinha NÃO deve bypassar.
 */
@RlsTest
class SuperAdminRlsTest extends PostgresIntegrationSupport {

    static final UUID TENANT_C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Autowired CategoryRepository categoryRepository;
    @Autowired RlsTestHelper       rlsHelper;
    @Autowired JdbcTemplate        jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT (id) DO NOTHING",
            TENANT_C.toString(), "Tenant C", "tenant-c-test");

        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_C + "'");
        jdbc.update(
            "INSERT INTO categories (id, tenant_id, name) VALUES " +
            "(gen_random_uuid(), ?::uuid, 'Cat Tenant C')",
            TENANT_C.toString());
    }

    @Test
    @DisplayName("Kill Switch completo bypassa RLS para SUPER_ADMIN")
    void killSwitch_bothConditions_shouldBypass() {
        rlsHelper.runAsSuperAdmin(() -> {
            var cats = categoryRepository.findAll();
            assertThat(cats).isNotEmpty();
        });
    }

    @Test
    @DisplayName("Apenas is_super_admin=true sem UUID master não bypassa")
    void onlySuperAdminFlag_withoutMasterUuid_shouldNotBypass() {
        // Flag ativa mas UUID é do tenant C, não do master
        jdbc.execute("SET LOCAL app.is_super_admin = 'true'");
        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_C + "'");

        var cats = categoryRepository.findAll();
        // Deve ver apenas dados do TENANT_C, não cross-tenant
        assertThat(cats).allSatisfy(c ->
            assertThat(c.getTenantId()).isEqualTo(TENANT_C));
    }

    @Test
    @DisplayName("Apenas UUID master sem flag não bypassa RLS")
    void onlyMasterUuid_withoutFlag_shouldNotBypass() {
        // UUID master sem a flag — master não tem categorias próprias
        jdbc.execute("SET LOCAL app.current_tenant = '" +
            MasterTenantConstants.MASTER_TENANT_ID_STR + "'");

        var cats = categoryRepository.findAll();
        assertThat(cats).isEmpty(); // master não tem dados de negócio
    }
}
```

---

## Tarefa 8 — Verificar compilação

```bash
cd backend
./mvnw compile -q
```

Se compilar sem erros, seguir. Se houver erro de importação, ler o
arquivo de entidade correspondente e ajustar o import.

---

## Tarefa 9 — Rodar testes separadamente

```bash
# Apenas unitários — deve manter 59 testes (ou o número atual)
./mvnw test -q

# Apenas testes RLS — requer Docker rodando
./mvnw test -Dgroups=rls -Dexclude.integration.tests="" -q
```

Para o segundo comando, o Testcontainers vai:
1. Detectar Docker disponível
2. Baixar `postgres:16` se necessário
3. Aplicar migrations V1..V8
4. Executar 8 testes
5. Derrubar o container

Esperado:
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Tarefa 10 — Commit

```bash
git add backend/pom.xml
git add backend/src/test/resources/application-test.yml
git add backend/src/test/java/com/dipdv/support/
git add backend/src/test/java/com/dipdv/rls/

git diff --cached --stat

git commit -m "test(rls): infraestrutura Testcontainers e testes de isolamento RLS

- Dependencias: testcontainers 1.20.4 (postgresql, junit-jupiter)
- @RlsTest e @IntegrationTest: anotacoes por tipo de teste
- PostgresIntegrationSupport: container PostgreSQL 16 estatico por modulo
- RlsTestHelper: ativa SET LOCAL sem passar pelo JwtAuthFilter
- TenantIsolationRlsTest: 5 cenarios de isolamento entre tenants
- SuperAdminRlsTest: 3 cenarios do Kill Switch duplo
- Maven Surefire: tags para separar unitarios de integracao
- application-test.yml: perfil de teste para Testcontainers

8 novos testes de RLS com PostgreSQL real"

git push origin feature/test-rls-integration
```

---

## Checklist para reportar

- [ ] `./mvnw compile` sem erros
- [ ] `./mvnw test` → 59 testes unitários (número atual), BUILD SUCCESS
- [ ] `./mvnw test -Dgroups=rls -Dexclude.integration.tests=""` → 8 testes RLS, BUILD SUCCESS
- [ ] Log mostra Testcontainers subindo container postgres:16
- [ ] Log mostra Flyway aplicando 8 migrations
- [ ] PR aberto `feature/test-rls-integration` → `develop`
