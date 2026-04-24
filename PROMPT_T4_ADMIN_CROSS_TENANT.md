# Prompt T4 — Testes Admin Cross-Tenant (Claude CLI)

Leia AGENTS.md antes de começar.
Branch: feature/test-rls-integration

---

## Contexto

127 testes passando (59 unit + 10 RLS + 54 controller + 4 E2E).
Este prompt implementa testes do módulo SUPER_ADMIN:
operações cross-tenant, onboarding atômico e isolamento entre tenants.

## Padrões obrigatórios (consolidados em T1/T2/T3)

1. Container: Singleton via `static {}` — herdar `PostgresIntegrationSupport`
2. Setup: `JdbcTemplate` puro com `SET app.current_tenant` antes de INSERTs
3. UUID fixo do usuário de teste: `00000000-0000-0000-0001-000000000001`
4. Tenant dev: `00000000-0000-0000-0000-000000000001`
5. Master UUID: `ffffffff-ffff-ffff-ffff-ffffffffffff`
6. `ON CONFLICT DO NOTHING` em todos os INSERTs de setup
7. `@BeforeEach` limpa estado via SQL

---

## Tarefa — Criar AdminCrossTenantIT.java

**Arquivo:** `backend/src/test/java/com/dipdv/e2e/AdminCrossTenantIT.java`

```java
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes E2E do módulo SUPER_ADMIN:
 * - Acesso cross-tenant a dados de qualquer tenant
 * - Onboarding atômico de novo tenant + owner
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

        // Limpar tenants criados por testes anteriores (exceto dev e master)
        jdbc.update(
            "DELETE FROM users WHERE tenant_id NOT IN " +
            "(?::uuid, ?::uuid) AND email LIKE '%@e2e-test.com'",
            DEV_TENANT_ID.toString(), MASTER_TENANT_ID.toString());
        jdbc.update(
            "DELETE FROM tenants WHERE id NOT IN (?::uuid, ?::uuid) " +
            "AND slug LIKE '%-e2e'",
            DEV_TENANT_ID.toString(), MASTER_TENANT_ID.toString());
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
    @DisplayName("Onboarding atômico: criar tenant + owner em transação única")
    void createTenant_shouldCreateTenantAndOwnerAtomically() throws Exception {
        String slug = "lanchonete-e2e-" + System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lanchonete E2E Test",
                      "slug": "%s",
                      "ownerEmail": "owner@e2e-test.com",
                      "ownerName": "Owner E2E",
                      "ownerPassword": "Senha@123"
                    }
                """.formatted(slug)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lanchonete E2E Test"))
            .andExpect(jsonPath("$.planType").value("TRIAL"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

        String newTenantId = id(result);
        assertThat(newTenantId).isNotBlank();

        // Verificar que o owner foi criado no banco
        Integer ownerCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users " +
            "WHERE tenant_id = ?::uuid AND email = 'owner@e2e-test.com'",
            Integer.class, newTenantId);
        assertThat(ownerCount).isEqualTo(1);
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
                      "slug": "%s",
                      "ownerEmail": "dup1@e2e-test.com",
                      "ownerName": "Dup 1",
                      "ownerPassword": "Senha@123"
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
                      "slug": "%s",
                      "ownerEmail": "dup2@e2e-test.com",
                      "ownerName": "Dup 2",
                      "ownerPassword": "Senha@123"
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
                      "slug": "%s",
                      "ownerEmail": "suspend@e2e-test.com",
                      "ownerName": "Suspend Owner",
                      "ownerPassword": "Senha@123"
                    }
                """.formatted(slug)))
            .andExpect(status().isCreated())
            .andReturn();

        String tenantId = id(createResult);

        // Login deve funcionar antes da suspensão
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "%s",
                      "email": "suspend@e2e-test.com",
                      "password": "Senha@123"
                    }
                """.formatted(tenantId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());

        // Suspender tenant
        mockMvc.perform(delete("/api/v1/admin/tenants/" + tenantId)
                .header("Authorization", superAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Teste de suspensão E2E\"}"))
            .andExpect(status().isOk());

        // Login deve ser bloqueado após suspensão
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "%s",
                      "email": "suspend@e2e-test.com",
                      "password": "Senha@123"
                    }
                """.formatted(tenantId)))
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
```

---

## Validação

```bash
cd backend

mvn compile -q && echo "COMPILE OK"

# Rodar apenas T4
mvn test -Dtest=AdminCrossTenantIT -Dexclude.integration.tests= 2>&1 | \
  grep -E "Tests run|FAILURE|ERROR" | tail -5

# Suite completa
mvn test -Dgroups=integration -Dexclude.integration.tests= 2>&1 | \
  grep -E "Tests run|FAILURE" | tail -3

# Todos incluindo unitários
mvn test -Dexclude.integration.tests= 2>&1 | \
  grep -E "Tests run|FAILURE" | tail -3
```

Metas:
- `AdminCrossTenantIT: Tests run: 7, Failures: 0`
- `Suite integração: Tests run: 65, Failures: 0`
- `Total: Tests run: 134+, Failures: 0`

---

## Relatório esperado

```
## Implementado
- e2e/AdminCrossTenantIT.java (criado — 7 testes)

## Testes
65 testes integração, 0 falhas — BUILD SUCCESS
13X total, 0 falhas — BUILD SUCCESS

## Desvios
- (qualquer desvio em 1 linha)
```
