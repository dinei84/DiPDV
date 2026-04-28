package com.dipdv.modules.admin.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class TenantAdminControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("SUPER_ADMIN lista tenants")
    void superAdminShouldListTenants() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("ADMIN comum não lista tenants")
    void adminShouldNotListTenants() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", tokenFor("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN cria tenant com módulos BASE habilitados")
    void superAdminShouldCreateTenantWithBaseModules() throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lanchonete Base Modules"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("lanchonete-base-modules"))
                .andExpect(jsonPath("$.enabledModules").isArray())
                .andExpect(jsonPath("$.enabledModules[?(@ == 'PDV_BASIC')]").exists())
                .andExpect(jsonPath("$.enabledModules[?(@ == 'CATALOG_MANAGEMENT')]").exists())
                .andReturn());

        UUID tenantId = UUID.fromString(response.get("id").asText());
        Integer enabledCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tenant_modules
                WHERE tenant_id = ?::uuid
                  AND module_code IN ('PDV_BASIC', 'CATALOG_MANAGEMENT')
                  AND enabled = true
                """,
                Integer.class,
                tenantId.toString()
        );

        assertThat(enabledCount).isEqualTo(2);
    }

    @Test
    @DisplayName("SUPER_ADMIN atualiza nome do tenant")
    void superAdminShouldUpdateTenantName() throws Exception {
        UUID tenantId = createTenant("tenant-update-name").id();

        mockMvc.perform(put("/api/v1/admin/tenants/{tenantId}", tenantId)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Tenant Atualizado"
                    }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tenant Atualizado"));

        mockMvc.perform(get("/api/v1/admin/tenants/{tenantId}", tenantId)
                .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tenant Atualizado"));
    }

    @Test
    @DisplayName("SUPER_ADMIN desativa tenant e bloqueia login")
    void superAdminShouldDeactivateTenantAndBlockLogin() throws Exception {
        CreatedTenant createdTenant = createTenant("tenant-login-block");
        UUID tenantId = createdTenant.id();

        jdbcTemplate.execute("SET LOCAL app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'");
        jdbcTemplate.execute("SET LOCAL app.is_super_admin = 'true'");
        jdbcTemplate.update(
                """
                INSERT INTO users (tenant_id, email, password_hash, name, role, active)
                VALUES (?::uuid, ?, ?, ?, 'ADMIN'::user_role, true)
                """,
                tenantId.toString(),
                "admin.blocked@dipdv.dev",
                passwordEncoder.encode("Senha@123"),
                "Admin Blocked"
        );

        mockMvc.perform(put("/api/v1/admin/tenants/{tenantId}", tenantId)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "active": false
                    }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "%s",
                      "email": "admin.blocked@dipdv.dev",
                      "password": "Senha@123"
                    }
                """.formatted(tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("SUPER_ADMIN busca tenant inexistente")
    void superAdminShouldReturn404ForMissingTenant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants/{tenantId}",
                        UUID.fromString("00000000-0000-0000-0000-000000000099"))
                .header("Authorization", superAdminToken()))
                .andExpect(status().isNotFound());
    }

    private CreatedTenant createTenant(String slug) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Tenant %s",
                      "slug": "%s"
                    }
                """.formatted(slug, slug)))
                .andExpect(status().isCreated())
                .andReturn());

        return new CreatedTenant(UUID.fromString(response.get("id").asText()));
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record CreatedTenant(UUID id) {}
}
