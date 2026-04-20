# Prompt T1 — Testes de Isolamento RLS + Infraestrutura Testcontainers

---

## Contexto

77 testes unitários passando. Nenhum teste valida o isolamento real entre
tenants via RLS. Este prompt implementa a infraestrutura de testes de
integração e os primeiros testes críticos: provar que tenant A nunca
vê dados do tenant B, independente de qualquer falha no código Java.

**Branch:** `feature/test-rls-integration` a partir de `develop`

---

## Por que Testcontainers + PostgreSQL real

H2 não suporta:
- `CREATE TYPE` (ENUMs nativos do PostgreSQL)
- `ENABLE ROW LEVEL SECURITY`
- `current_setting()` usado nas policies RLS
- `EXCLUDE USING btree` (constraint do cash_register)

Sem PostgreSQL real, testes de RLS são impossíveis. O Testcontainers
sobe um PostgreSQL via Docker dentro do próprio processo de teste,
aplica todas as migrations Flyway e descarta o container ao final.

---

## Tarefa 1 — Dependências no pom.xml

Adicionar dentro de `<dependencies>`:

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

---

## Tarefa 2 — Anotações customizadas

### @IntegrationTest
**Arquivo:** `test/java/com/dipdv/support/IntegrationTest.java`

```java
package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Marca testes de integração que sobem o contexto Spring completo
 * com banco PostgreSQL real via Testcontainers.
 *
 * Uso: @IntegrationTest na classe de teste.
 * Herança: a classe deve estender AbstractModuleIntegrationTest
 * ou um dos módulos específicos.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
```

### @RlsTest
**Arquivo:** `test/java/com/dipdv/support/RlsTest.java`

```java
package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Marca testes que validam o isolamento de dados entre tenants via RLS.
 *
 * Estes são os testes mais críticos do sistema — provam que nenhuma
 * falha no código Java permite que tenant A veja dados do tenant B.
 * O RLS do PostgreSQL é a última linha de defesa.
 *
 * Todo teste @RlsTest deve:
 * 1. Criar dados em pelo menos 2 tenants distintos
 * 2. Ativar contexto de um tenant específico
 * 3. Verificar que apenas os dados daquele tenant são visíveis
 * 4. Verificar que dados dos outros tenants são invisíveis
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

## Tarefa 3 — Classe base de integração

**Arquivo:** `test/java/com/dipdv/support/PostgresIntegrationSupport.java`

```java
package com.dipdv.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Suporte base para testes com PostgreSQL real via Testcontainers.
 *
 * O container é estático — compartilhado entre todos os testes da classe
 * que herdar este suporte. Isso significa que as migrations Flyway rodam
 * uma única vez por módulo, tornando a suite mais rápida.
 *
 * Ciclo de vida:
 * 1. @BeforeAll: container sobe, Flyway aplica V1..V8
 * 2. @BeforeEach: cada teste limpa os dados que criou (via @Transactional rollback)
 * 3. @AfterAll: container derruba (Ryuk cuida disso automaticamente)
 */
@Testcontainers
public abstract class PostgresIntegrationSupport {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("dipdv_test")
            .withUsername("dipdv_app")
            .withPassword("dipdv_test");

    /**
     * Injeta as propriedades do container no contexto Spring
     * antes de qualquer bean ser criado.
     * O Flyway usará estas configurações para aplicar as migrations.
     */
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

## Tarefa 4 — application-test.yml

**Arquivo:** `test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dipdv_test   # sobrescrito pelo Testcontainers
    username: dipdv_app
    password: dipdv_test
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false                                     # desligar no CI
  flyway:
    enabled: true
    locations: classpath:db/migration

logging:
  level:
    com.dipdv: WARN                                     # menos ruído nos testes
    org.flywaydb: INFO                                  # ver migrations rodando
    org.testcontainers: WARN

dipdv:
  jwt:
    secret: test-secret-key-minimo-256-bits-para-hmac-sha256-aqui-test
    expiration-ms: 3600000
```

---

## Tarefa 5 — Helper de contexto RLS para testes

**Arquivo:** `test/java/com/dipdv/support/RlsTestHelper.java`

```java
package com.dipdv.support;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Helper para ativar contexto de tenant nos testes de integração.
 *
 * Necessário porque os testes não passam pelo JwtAuthFilter/TenantFilter
 * do ciclo de vida HTTP. Precisamos ativar o RLS manualmente.
 *
 * Uso:
 *   rlsHelper.runAsTenant(TENANT_A_ID, () -> {
 *       List<Order> orders = orderRepository.findAll();
 *       assertThat(orders).allMatch(o -> o.getTenantId().equals(TENANT_A_ID));
 *   });
 */
@Component
public class RlsTestHelper {

    private final EntityManager entityManager;

    public RlsTestHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Executa uma ação no contexto de um tenant específico.
     * Ativa o RLS via SET LOCAL app.current_tenant.
     */
    @Transactional
    public void runAsTenant(UUID tenantId, Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantId + "'"
        ).executeUpdate();

        action.run();
    }

    /**
     * Executa uma ação no contexto do SUPER_ADMIN (bypass do RLS).
     */
    @Transactional
    public void runAsSuperAdmin(Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.is_super_admin = 'true'; " +
            "SET LOCAL app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'"
        ).executeUpdate();

        action.run();
    }

    /**
     * Insere dados diretamente no banco bypassando RLS.
     * Usado no setup de testes para criar dados de múltiplos tenants.
     */
    @Transactional
    public void insertAsSuperAdmin(Runnable insertAction) {
        runAsSuperAdmin(insertAction);
    }
}
```

---

## Tarefa 6 — Testes de isolamento RLS (o mais crítico)

**Arquivo:** `test/java/com/dipdv/rls/TenantIsolationRlsTest.java`

```java
package com.dipdv.rls;

import com.dipdv.modules.catalog.entity.Category;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.modules.order.entity.Order;
import com.dipdv.modules.order.entity.enums.OrderStatus;
import com.dipdv.modules.order.repository.OrderRepository;
import com.dipdv.shared.audit.AuditLog;
import com.dipdv.shared.audit.AuditLogRepository;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.support.RlsTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de isolamento de dados entre tenants via RLS.
 *
 * Estes testes provam que o PostgreSQL RLS garante isolamento
 * independentemente de qualquer lógica no código Java.
 *
 * Cenário base: dois tenants (A e B) com dados distintos.
 * Cada teste ativa o contexto de um tenant e verifica que
 * apenas seus próprios dados são visíveis.
 */
@RlsTest
class TenantIsolationRlsTest extends PostgresIntegrationSupport {

    static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    static final UUID USER_A   = UUID.randomUUID();
    static final UUID USER_B   = UUID.randomUUID();

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository   productRepository;
    @Autowired OrderRepository     orderRepository;
    @Autowired AuditLogRepository  auditLogRepository;
    @Autowired RlsTestHelper       rlsHelper;
    @Autowired JdbcTemplate        jdbc;

    @BeforeEach
    void setUp() {
        // Limpar dados de testes anteriores
        rlsHelper.runAsSuperAdmin(() -> {
            jdbc.execute("DELETE FROM order_items WHERE order_id IN " +
                "(SELECT id FROM orders WHERE tenant_id IN " +
                "('aaaaaaaa-0000-0000-0000-000000000001'," +
                "'bbbbbbbb-0000-0000-0000-000000000002'))");
            jdbc.execute("DELETE FROM orders WHERE tenant_id IN " +
                "('aaaaaaaa-0000-0000-0000-000000000001'," +
                "'bbbbbbbb-0000-0000-0000-000000000002')");
            jdbc.execute("DELETE FROM products WHERE tenant_id IN " +
                "('aaaaaaaa-0000-0000-0000-000000000001'," +
                "'bbbbbbbb-0000-0000-0000-000000000002')");
            jdbc.execute("DELETE FROM categories WHERE tenant_id IN " +
                "('aaaaaaaa-0000-0000-0000-000000000001'," +
                "'bbbbbbbb-0000-0000-0000-000000000002')");
        });

        // Criar tenants de teste
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_A.toString(), "Tenant A", "tenant-a");
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_B.toString(), "Tenant B", "tenant-b");

        // Criar usuários
        String pwHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCkJ5yI0m.6kgJJ9q2Y5Jmi";
        jdbc.update(
            "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role) " +
            "ON CONFLICT DO NOTHING",
            USER_A.toString(), TENANT_A.toString(), "a@test.com", pwHash, "User A");
        jdbc.update(
            "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role) " +
            "ON CONFLICT DO NOTHING",
            USER_B.toString(), TENANT_B.toString(), "b@test.com", pwHash, "User B");

        // Criar categorias para cada tenant
        rlsHelper.runAsTenant(TENANT_A, () -> {
            Category cat = new Category();
            cat.setTenantId(TENANT_A);
            cat.setName("Lanches do Tenant A");
            categoryRepository.save(cat);
        });
        rlsHelper.runAsTenant(TENANT_B, () -> {
            Category cat = new Category();
            cat.setTenantId(TENANT_B);
            cat.setName("Lanches do Tenant B");
            categoryRepository.save(cat);
        });
    }

    // ─── Testes de Category ────────────────────────────────────────────────

    @Test
    @DisplayName("Tenant A só vê suas próprias categorias")
    void tenantA_shouldOnlySeeOwnCategories() {
        rlsHelper.runAsTenant(TENANT_A, () -> {
            List<Category> categories = categoryRepository.findAll();

            assertThat(categories)
                .isNotEmpty()
                .allSatisfy(c ->
                    assertThat(c.getTenantId()).isEqualTo(TENANT_A)
                );
            assertThat(categories)
                .noneMatch(c -> c.getTenantId().equals(TENANT_B));
        });
    }

    @Test
    @DisplayName("Tenant B não vê categorias do Tenant A")
    void tenantB_shouldNotSeeTenantACategories() {
        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<Category> categories = categoryRepository.findAll();

            assertThat(categories)
                .noneMatch(c -> c.getTenantId().equals(TENANT_A));
        });
    }

    // ─── Testes de Product ─────────────────────────────────────────────────

    @Test
    @DisplayName("Produto criado por tenant A é invisível para tenant B")
    void productCreatedByTenantA_shouldBeInvisibleToTenantB() {
        // Criar produto no tenant A
        rlsHelper.runAsTenant(TENANT_A, () -> {
            Product p = new Product();
            p.setTenantId(TENANT_A);
            p.setName("X-Burguer Tenant A");
            p.setPrice(BigDecimal.valueOf(18.90));
            p.setStockQuantity(10);
            p.setStockMinLevel(2);
            productRepository.save(p);
        });

        // Tenant B não deve ver o produto
        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<Product> products = productRepository.findAll();
            assertThat(products)
                .noneMatch(p -> p.getName().contains("Tenant A"));
        });
    }

    // ─── Testes de Order ───────────────────────────────────────────────────

    @Test
    @DisplayName("Pedido do tenant A é invisível para tenant B")
    void orderFromTenantA_shouldBeInvisibleToTenantB() {
        // Criar pedido no tenant A via SQL direto (evita validações de FK)
        UUID orderId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO orders (id, tenant_id, user_id, status, total, version) " +
            "VALUES (?::uuid, ?::uuid, ?::uuid, 'OPEN'::order_status, 0, 0)",
            orderId.toString(), TENANT_A.toString(), USER_A.toString());

        // Tenant B não deve ver o pedido
        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<Order> orders = orderRepository.findAll();
            assertThat(orders)
                .noneMatch(o -> o.getId().equals(orderId));
        });

        // Tenant A deve ver seu próprio pedido
        rlsHelper.runAsTenant(TENANT_A, () -> {
            List<Order> orders = orderRepository.findAll();
            assertThat(orders)
                .anyMatch(o -> o.getId().equals(orderId));
        });
    }

    // ─── Testes de Audit Log ───────────────────────────────────────────────

    @Test
    @DisplayName("Logs de auditoria são isolados por tenant")
    void auditLogs_shouldBeIsolatedByTenant() {
        // Inserir logs para ambos os tenants
        jdbc.update(
            "INSERT INTO audit_log (id, tenant_id, user_id, action, entity, entity_id) " +
            "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'TEST_ACTION', 'orders', gen_random_uuid())",
            TENANT_A.toString(), USER_A.toString());
        jdbc.update(
            "INSERT INTO audit_log (id, tenant_id, user_id, action, entity, entity_id) " +
            "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'TEST_ACTION', 'orders', gen_random_uuid())",
            TENANT_B.toString(), USER_B.toString());

        // Tenant A só vê seus logs
        rlsHelper.runAsTenant(TENANT_A, () -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs)
                .isNotEmpty()
                .allSatisfy(l ->
                    assertThat(l.getTenantId()).isEqualTo(TENANT_A)
                );
        });

        // Tenant B só vê seus logs
        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs)
                .isNotEmpty()
                .allSatisfy(l ->
                    assertThat(l.getTenantId()).isEqualTo(TENANT_B)
                );
        });
    }

    // ─── Testes do SUPER_ADMIN ─────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN vê dados de todos os tenants")
    void superAdmin_shouldSeeAllTenants() {
        rlsHelper.runAsSuperAdmin(() -> {
            List<Category> categories = categoryRepository.findAll();
            assertThat(categories)
                .extracting(Category::getTenantId)
                .contains(TENANT_A, TENANT_B);
        });
    }

    @Test
    @DisplayName("Contexto sem tenant definido não vê nenhum dado")
    void noTenantContext_shouldSeeNoData() {
        // Sem SET LOCAL, current_setting retorna NULL
        // A policy retorna NULL = NULL que é falso → nenhum dado visível
        List<Category> categories = categoryRepository.findAll();
        assertThat(categories)
            .noneMatch(c ->
                c.getTenantId().equals(TENANT_A) ||
                c.getTenantId().equals(TENANT_B)
            );
    }
}
```

---

## Tarefa 7 — Testes de RLS no Kill Switch SUPER_ADMIN

**Arquivo:** `test/java/com/dipdv/rls/SuperAdminRlsTest.java`

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes específicos do Kill Switch do SUPER_ADMIN no RLS.
 *
 * Valida que o bypass exige AMBAS as condições simultaneamente:
 * 1. app.is_super_admin = 'true'
 * 2. app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
 *
 * Se apenas uma das condições estiver ativa, o bypass NÃO ocorre.
 */
@RlsTest
class SuperAdminRlsTest extends PostgresIntegrationSupport {

    static final UUID TENANT_A = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Autowired CategoryRepository categoryRepository;
    @Autowired RlsTestHelper       rlsHelper;
    @Autowired JdbcTemplate        jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_A.toString(), "Tenant C", "tenant-c");

        rlsHelper.runAsTenant(TENANT_A, () -> {
            // Inserir categoria de teste
            jdbc.update(
                "INSERT INTO categories (id, tenant_id, name) " +
                "VALUES (gen_random_uuid(), ?::uuid, 'Categoria C')",
                TENANT_A.toString());
        });
    }

    @Test
    @DisplayName("is_super_admin=true SEM UUID master não bypassa RLS")
    void superAdminFlag_withoutMasterUuid_shouldNotBypassRls() {
        // Setar apenas a flag sem o UUID master — Kill Switch deve bloquear
        jdbc.execute("SET LOCAL app.is_super_admin = 'true'");
        jdbc.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "'");

        // Deve ver apenas dados do TENANT_A, não de outros
        var categories = categoryRepository.findAll();
        assertThat(categories)
            .allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(TENANT_A));
    }

    @Test
    @DisplayName("Kill Switch completo dá acesso cross-tenant ao SUPER_ADMIN")
    void killSwitch_withBothConditions_shouldBypassRls() {
        rlsHelper.runAsSuperAdmin(() -> {
            var categories = categoryRepository.findAll();
            // SUPER_ADMIN com UUID master vê TODAS as categorias
            assertThat(categories).isNotEmpty();
        });
    }

    @Test
    @DisplayName("UUID master sem flag is_super_admin não bypassa RLS")
    void masterUuid_withoutSuperAdminFlag_shouldNotBypassRls() {
        // Setar apenas o UUID master sem a flag — Kill Switch deve bloquear
        jdbc.execute(
            "SET LOCAL app.current_tenant = '" +
            MasterTenantConstants.MASTER_TENANT_ID_STR + "'");

        // Sem a flag is_super_admin, tenant master não tem dados próprios
        var categories = categoryRepository.findAll();
        assertThat(categories).isEmpty();
    }
}
```

---

## Tarefa 8 — Configuração do Maven para separar testes

Adicionar no `pom.xml` para permitir rodar testes por tag:

```xml
<build>
    <plugins>
        <!-- Plugin existente — adicionar configuração de tags -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- Por padrão, excluir testes de integração (mais lentos) -->
                <excludedGroups>${exclude.integration.tests}</excludedGroups>
            </configuration>
        </plugin>
    </plugins>
</build>

<properties>
    <!-- Padrão: excluir integração no build rápido -->
    <exclude.integration.tests>integration</exclude.integration.tests>
</properties>
```

Isso permite:
```bash
# Build rápido (apenas unitários)
.\mvnw.cmd test

# Apenas testes de RLS
.\mvnw.cmd test -Dgroups=rls -Dexclude.integration.tests=""

# Apenas testes de integração
.\mvnw.cmd test -Dgroups=integration -Dexclude.integration.tests=""

# Todos os testes
.\mvnw.cmd test -Dexclude.integration.tests=""
```

---

## Tarefa 9 — Validação

### 9a. Rodar apenas testes unitários (deve ser igual ao atual)

```bash
cd backend
.\mvnw.cmd test
```
Esperado: 77 testes, BUILD SUCCESS. (testes RLS excluídos por padrão)

### 9b. Rodar testes de RLS (requer Docker)

```bash
.\mvnw.cmd test -Dgroups=rls -Dexclude.integration.tests=""
```

O Testcontainers vai:
1. Baixar imagem `postgres:16` (apenas na primeira vez)
2. Subir o container
3. Aplicar migrations V1..V8 via Flyway
4. Executar os testes
5. Derrubar o container

Esperado no console:
```
🐳 Starting PostgreSQL container...
Flyway: Successfully applied 8 migrations
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 9c. Verificar falha intencional

Comentar temporariamente a policy de uma tabela no banco de teste
e confirmar que o teste detecta a brecha:

```bash
# NÃO fazer em produção — apenas para provar que o teste funciona
# O teste deve FALHAR se o RLS for removido
```

---

## Tarefa 10 — Commit

```bash
git add backend/pom.xml
git add backend/src/test/java/com/dipdv/support/
git add backend/src/test/java/com/dipdv/rls/
git add backend/src/test/resources/application-test.yml

git commit -m "test(rls): infraestrutura Testcontainers e testes de isolamento RLS

- Dependencias: testcontainers, postgresql, junit-jupiter
- @IntegrationTest e @RlsTest: anotacoes customizadas por tipo
- PostgresIntegrationSupport: container estatico por modulo
- RlsTestHelper: ativa contexto de tenant nos testes sem HTTP
- TenantIsolationRlsTest: 6 testes — tenant A nao ve dados de B
- SuperAdminRlsTest: 3 testes — Kill Switch duplo verificado
- application-test.yml: perfil de teste para Testcontainers
- Maven Surefire: tags para separar unitarios de integracao

Total: 9 novos testes de RLS (rodando com PostgreSQL real)"

git push origin feature/test-rls-integration
```

---

## Checklist

- [ ] `.\mvnw.cmd test` → 77 testes (unitários), BUILD SUCCESS
- [ ] `.\mvnw.cmd test -Dgroups=rls -Dexclude.integration.tests=""` → 9 testes, BUILD SUCCESS
- [ ] Log do Testcontainers mostra container PostgreSQL subindo
- [ ] Log do Flyway mostra 8 migrations aplicadas
- [ ] `TenantIsolationRlsTest`: todos os 6 cenários passando
- [ ] `SuperAdminRlsTest`: todos os 3 cenários passando
- [ ] PR aberto `feature/test-rls-integration` → `develop`

---

## O que vem no Prompt T2

- Testes de Controller com MockMvc + `@SpringBootTest`
- Verificar status HTTP corretos (200, 201, 400, 401, 403, 404, 409)
- Verificar que `@PreAuthorize` bloqueia roles incorretas
- Verificar formato do body de response e erros

## O que vem no Prompt T3

- Teste de fluxo completo de venda com Testcontainers
- Abrir pedido → adicionar item → fechar → pagar → verificar estoque → NFC-e
