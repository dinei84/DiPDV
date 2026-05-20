package com.dipdv.modules.user.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class UserManagementControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("SUPER_ADMIN cria primeiro ADMIN do tenant")
    void superAdminShouldCreateFirstAdmin() throws Exception {
        UUID tenantId = createTenant("tenant-first-admin");

        mockMvc.perform(post("/api/v1/admin/tenants/{tenantId}/users", tenantId)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin.first@cliente.com",
                      "name": "Admin First",
                      "password": "123"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("admin.first@cliente.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("SUPER_ADMIN recebe 409 ao criar ADMIN com email ativo duplicado")
    void superAdminShouldReturn409ForActiveEmailConflict() throws Exception {
        UUID tenantId = createTenant("tenant-admin-dup");
        createUser(tenantId, "admin.dup@cliente.com", "Admin Dup", "ADMIN", true);

        mockMvc.perform(post("/api/v1/admin/tenants/{tenantId}/users", tenantId)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin.dup@cliente.com",
                      "name": "Admin Duplicado",
                      "password": "123"
                    }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um usuário ativo com este email na plataforma"));
    }

    @Test
    @DisplayName("ADMIN cria CASHIER")
    void adminShouldCreateCashier() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "cashier.create@cliente.com",
                      "name": "Cashier Create",
                      "role": "CASHIER",
                      "password": "123"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cashier.create@cliente.com"))
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    @DisplayName("ADMIN não cria ADMIN")
    void adminShouldNotCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin.block@cliente.com",
                      "name": "Admin Block",
                      "role": "ADMIN",
                      "password": "123"
                    }
                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN não desativa a si mesmo")
    void adminShouldNotDeactivateSelf() throws Exception {
        UUID adminId = UUID.randomUUID();
        createUser(TENANT_ID, adminId, "admin.self@cliente.com", "Admin Self", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/users/{id}", adminId)
                .header("Authorization", bearer(adminId, TENANT_ID, "ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ADMIN reativa usuário desativado")
    void adminShouldReactivateInactiveUser() throws Exception {
        UUID userId = UUID.randomUUID();
        createUser(TENANT_ID, userId, "cashier.inactive@cliente.com", "Cashier Inactive", "CASHIER", false);

        mockMvc.perform(patch("/api/v1/users/{id}/reactivate", userId)
                .header("Authorization", tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("ADMIN tenant A lista users sem ver tenant B")
    void adminShouldOnlyListOwnTenantUsers() throws Exception {
        UUID tenantB = createTenant("tenant-users-b");
        createUser(TENANT_ID, "cashier.a@cliente.com", "Cashier A", "CASHIER", true);
        createUser(tenantB, "cashier.b@cliente.com", "Cashier B", "CASHIER", true);

        JsonNode response = json(mockMvc.perform(get("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andReturn());

        String body = response.toString();
        assertThat(body).contains("cashier.a@cliente.com");
        assertThat(body).doesNotContain("cashier.b@cliente.com");
    }

    @Test
    @DisplayName("Conflito de email entre usuários ativos de tenants DIFERENTES retorna 409")
    void crossTenantEmailConflictShouldReturn409() throws Exception {
        UUID tenantB = createTenant("tenant-b-conflict");
        createUser(tenantB, "global@duplicado.com", "User B", "ADMIN", true);

        // Tenta criar no tenant A (TENANT_ID) o mesmo email que está ativo no B
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "global@duplicado.com",
                      "name": "User A",
                      "role": "CASHIER",
                      "password": "123"
                    }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um usuário ativo com este email na plataforma"));
    }

    @Test
    @DisplayName("Conflito de email entre ativos no mesmo tenant retorna 409")
    void activeEmailConflictShouldReturn409() throws Exception {
        createUser(TENANT_ID, "cashier.conflict@cliente.com", "Cashier Conflict", "CASHIER", true);

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "cashier.conflict@cliente.com",
                      "name": "Cashier Conflict 2",
                      "role": "CASHIER",
                      "password": "123"
                    }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um usuário ativo com este email na plataforma"));
    }

    @Test
    @DisplayName("Email de usuário inativo pode ser reutilizado")
    void inactiveEmailShouldBeReusable() throws Exception {
        createUser(TENANT_ID, "cashier.reuse@cliente.com", "Cashier Reuse Old", "CASHIER", false);

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "cashier.reuse@cliente.com",
                      "name": "Cashier Reuse New",
                      "role": "CASHIER",
                      "password": "123"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cashier.reuse@cliente.com"));
    }

    private UUID createTenant(String slug) {
        applySuperAdminSqlContext();
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tenants (id, name, slug, active, plan_type)
                VALUES (?::uuid, ?, ?, true, 'TRIAL'::tenant_plan)
                """,
                tenantId.toString(),
                "Tenant " + slug,
                slug
        );
        return tenantId;
    }

    private UUID createUser(UUID tenantId, String email, String name, String role, boolean active) {
        UUID userId = UUID.randomUUID();
        createUser(tenantId, userId, email, name, role, active);
        return userId;
    }

    private void createUser(UUID tenantId, UUID userId, String email, String name, String role, boolean active) {
        applySuperAdminSqlContext();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, tenant_id, email, password_hash, name, role, active)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::user_role, ?)
                """,
                userId.toString(),
                tenantId.toString(),
                email,
                passwordEncoder.encode("123"),
                name,
                role,
                active
        );
    }

    private void applySuperAdminSqlContext() {
        jdbcTemplate.execute("SET app.is_super_admin = 'true'");
        jdbcTemplate.execute("SET app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'");
    }

    private String bearer(UUID userId, UUID tenantId, String role) {
        return "Bearer " + jwtService.generateToken(userId, tenantId, role);
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
