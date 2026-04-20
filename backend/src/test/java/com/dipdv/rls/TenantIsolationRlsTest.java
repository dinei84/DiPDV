package com.dipdv.rls;

import com.dipdv.modules.catalog.entity.Category;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.modules.order.entity.Order;
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
        // 1. Limpeza via kill switch (bypassa RLS; respeita ordem de FK)
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
            // audit_log é append-only: dipdv_app não tem DELETE (V2 revoca deliberadamente)
            jdbc.execute("DELETE FROM users WHERE tenant_id IN " +
                "('aaaaaaaa-0000-0000-0000-000000000001'," +
                "'bbbbbbbb-0000-0000-0000-000000000002')");
        });

        // 2. Tenants não tem RLS — inserir antes de users (FK users.tenant_id → tenants.id)
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_A.toString(), "Tenant A", "tenant-a");
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_B.toString(), "Tenant B", "tenant-b");

        // 3. Users via kill switch (WITH CHECK do RLS exige contexto; kill switch bypassa)
        String pwHash = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCkJ5yI0m.6kgJJ9q2Y5Jmi";
        rlsHelper.runAsSuperAdmin(() -> {
            jdbc.update(
                "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role)",
                USER_A.toString(), TENANT_A.toString(), "a@test.com", pwHash, "User A");
            jdbc.update(
                "INSERT INTO users (id, tenant_id, email, password_hash, name, role) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?, 'ADMIN'::user_role)",
                USER_B.toString(), TENANT_B.toString(), "b@test.com", pwHash, "User B");
        });

        // 4. Dados de negócio em contexto de tenant
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
        rlsHelper.runAsTenant(TENANT_A, () -> {
            Product p = new Product();
            p.setTenantId(TENANT_A);
            p.setName("X-Burguer Tenant A");
            p.setPrice(BigDecimal.valueOf(18.90));
            p.setStockQuantity(10);
            p.setStockMinLevel(2);
            productRepository.save(p);
        });

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
        UUID orderId = UUID.randomUUID();
        // INSERT em orders requer contexto de tenant (WITH CHECK do RLS)
        rlsHelper.runAsTenant(TENANT_A, () ->
            jdbc.update(
                "INSERT INTO orders (id, tenant_id, user_id, status, total, version) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, 'OPEN'::order_status, 0, 0)",
                orderId.toString(), TENANT_A.toString(), USER_A.toString())
        );

        rlsHelper.runAsTenant(TENANT_B, () -> {
            List<Order> orders = orderRepository.findAll();
            assertThat(orders)
                .noneMatch(o -> o.getId().equals(orderId));
        });

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
        // audit_log tem RLS — INSERT exige contexto de tenant
        rlsHelper.runAsTenant(TENANT_A, () ->
            jdbc.update(
                "INSERT INTO audit_log (id, tenant_id, user_id, action, entity, entity_id) " +
                "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'TEST_ACTION', 'orders', gen_random_uuid())",
                TENANT_A.toString(), USER_A.toString())
        );
        rlsHelper.runAsTenant(TENANT_B, () ->
            jdbc.update(
                "INSERT INTO audit_log (id, tenant_id, user_id, action, entity, entity_id) " +
                "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'TEST_ACTION', 'orders', gen_random_uuid())",
                TENANT_B.toString(), USER_B.toString())
        );

        rlsHelper.runAsTenant(TENANT_A, () -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs)
                .isNotEmpty()
                .allSatisfy(l ->
                    assertThat(l.getTenantId()).isEqualTo(TENANT_A)
                );
        });

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
