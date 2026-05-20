package com.dipdv.e2e;

import com.dipdv.support.IntegrationTest;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes E2E do módulo SUPER_ADMIN:
 * - Acesso cross-tenant a dados de qualquer tenant
 * - CRUD de tenants para o painel SUPER_ADMIN
 * - Métricas globais consolidadas
 * - Suspensão de tenant bloqueia login
 * - Isolamento: SUPER_ADMIN não vê dados via endpoints de tenant normal
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminCrossTenantIT extends PostgresIntegrationSupport {

    static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    static final UUID DEV_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID =
        UUID.fromString("00000000-0000-0000-0001-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    String superAdminToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        superAdminToken = "Bearer " + jwtService.generateToken(
            USER_ID, MASTER_TENANT_ID, "SUPER_ADMIN");
        adminToken = "Bearer " + jwtService.generateToken(
            USER_ID, DEV_TENANT_ID, "ADMIN");

        // Garantir tenant dev existe
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            DEV_TENANT_ID.toString(), "Lanchonete Dev", "dev-tenant");

        // Limpar dados criados por este teste (E2E) especificamente
        // ORDEM CRÍTICA: respeitar dependências FK
        // 1. Deletar produtos (referem categories)
        jdbc.update(
            "DELETE FROM products WHERE tenant_id IN " +
            "(SELECT id FROM tenants WHERE slug LIKE '%-e2e')");

        // 2. Deletar categorias (auto-criadas pelo TenantAdminService)
        jdbc.update(
            "DELETE FROM categories WHERE tenant_id IN " +
            "(SELECT id FROM tenants WHERE slug LIKE '%-e2e')");

        // 3. Deletar usuários
        jdbc.update(
            "DELETE FROM users WHERE tenant_id IN " +
            "(SELECT id FROM tenants WHERE slug LIKE '%-e2e')");

        // 4. Deletar tenant_modules
        jdbc.update(
            "DELETE FROM tenant_modules WHERE tenant_id IN " +
            "(SELECT id FROM tenants WHERE slug LIKE '%-e2e')");

        // 5. Deletar tenants
        jdbc.update(
            "DELETE FROM tenants WHERE slug LIKE '%-e2e'");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    String id(MvcResult result) throws Exception {
        return json(result).get("id").asText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN lista todos os tenants incluindo dev")
    void listTenants_asSuperAdmin_shouldIncludeAllTenants() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode tenants = json(result);
        assertThat(tenants.isArray()).isTrue();

        boolean hasDevTenant = false;
        for (JsonNode t : tenants) {
            if (DEV_TENANT_ID.toString().equals(t.get("id").asText())) {
                hasDevTenant = true;
                break;
            }
        }
        assertThat(hasDevTenant)
            .as("Dev tenant deve aparecer na lista do SUPER_ADMIN")
            .isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN vê métricas globais consolidadas")
    void globalStats_asSuperAdmin_shouldReturnMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                .header("Authorization", superAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantCount").isNumber())
            .andExpect(jsonPath("$.activeTenantCount").isNumber())
            .andExpect(jsonPath("$.totalOrders").isNumber())
            .andExpect(jsonPath("$.totalRevenue").isNumber());
    }

    @Test
    @DisplayName("Criação de tenant ativa módulos BASE")
    void createTenant_shouldEnableBaseModules() throws Exception {
        String slug = "lanchonete-e2e-" + System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lanchonete E2E Test",
                      "slug": "%s"
                    }
                """.formatted(slug)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lanchonete E2E Test"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.enabledModules[?(@ == 'PDV_BASIC')]").exists())
            .andExpect(jsonPath("$.enabledModules[?(@ == 'CATALOG_MANAGEMENT')]").exists())
            .andReturn();

        String newTenantId = id(result);
        assertThat(newTenantId).isNotBlank();

        Integer enabledModules = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_modules " +
            "WHERE tenant_id = ?::uuid AND module_code IN ('PDV_BASIC', 'CATALOG_MANAGEMENT') AND enabled = true",
            Integer.class, newTenantId);
        assertThat(enabledModules).isEqualTo(2);
    }

    @Test
    @DisplayName("Slug duplicado retorna 409")
    void createTenant_withDuplicateSlug_shouldReturn409() throws Exception {
        String slug = "slug-duplicado-e2e";

        // Criar primeira vez
        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Tenant Slug Dup 1",
                      "slug": "%s"
                    }
                """.formatted(slug)))
            .andExpect(status().isCreated());

        // Criar segunda vez com mesmo slug
        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Tenant Slug Dup 2",
                      "slug": "%s"
                    }
                """.formatted(slug)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Suspender tenant bloqueia login dos usuários")
    void suspendTenant_shouldBlockLogin() throws Exception {
        // Criar tenant
        String slug = "tenant-suspend-e2e-" + System.currentTimeMillis();
        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Tenant Para Suspender",
                      "slug": "%s"
                    }
                """.formatted(slug)))
            .andExpect(status().isCreated())
            .andReturn();

        String tenantId = id(createResult);

        jdbc.execute("SET LOCAL app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'");
        jdbc.execute("SET LOCAL app.is_super_admin = 'true'");
        jdbc.update(
            "INSERT INTO users (tenant_id, email, password_hash, name, role, active) " +
            "VALUES (?::uuid, ?, ?, ?, 'ADMIN'::user_role, true)",
            tenantId,
            "suspend@e2e-test.com",
            passwordEncoder.encode("Senha@123"),
            "Suspend Owner"
        );

        // Login deve funcionar antes da suspensão
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "suspend@e2e-test.com",
                      "password": "Senha@123"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());

        // Suspender tenant
        mockMvc.perform(put("/api/v1/admin/tenants/" + tenantId)
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        // Login deve ser bloqueado após suspensão
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "suspend@e2e-test.com",
                      "password": "Senha@123"
                    }
                """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("SUPER_ADMIN vê engajamento por tenant")
    void engagementMetrics_asSuperAdmin_shouldReturnAllTenants() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/engagement")
                .header("Authorization", superAdminToken))
            .andExpect(status().isOk())
            .andExpect(result -> {
                JsonNode metrics = json(result);
                assertThat(metrics.isArray()).isTrue();
            });
    }

    @Test
    @DisplayName("ADMIN normal não acessa endpoints /admin/**")
    void adminEndpoints_withRegularAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", adminToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("SUPER_ADMIN vê detalhe 360 de um tenant")
    void tenantSummary_asSuperAdmin_shouldReturnFullDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/" +
                DEV_TENANT_ID + "/summary")
                .header("Authorization", superAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DEV_TENANT_ID.toString()))
            .andExpect(jsonPath("$.userCount").isNumber())
            .andExpect(jsonPath("$.orderCount").isNumber())
            .andExpect(jsonPath("$.totalRevenue").isNumber());
    }
}
