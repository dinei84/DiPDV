package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class AdminControllerIT extends ControllerIntegrationSupport {

    @Test
    @DisplayName("GET /admin/tenants com SUPER_ADMIN → 200")
    void listTenants_withSuperAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /admin/tenants com ADMIN → 403")
    void listTenants_withAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", tokenFor("ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/dashboard/stats com SUPER_ADMIN → 200")
    void globalStats_withSuperAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                .header("Authorization", superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantCount").isNumber())
            .andExpect(jsonPath("$.totalRevenue").isNumber());
    }

    @Test
    @DisplayName("POST /admin/tenants com SUPER_ADMIN → 201")
    void createTenant_withSuperAdmin_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lanchonete IT Test",
                      "slug": "lanchonete-it-test",
                      "ownerEmail": "owner@it-test.com",
                      "ownerName": "Owner IT",
                      "ownerPassword": "Senha@123"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lanchonete IT Test"))
            .andExpect(jsonPath("$.planType").value("TRIAL"));
    }

    @Test
    @DisplayName("POST /admin/tenants com slug duplicado → 409")
    void createTenant_withDuplicateSlug_shouldReturn409() throws Exception {
        String body = """
            {
              "name": "Tenant Slug Dup",
              "slug": "slug-duplicado-it",
              "ownerEmail": "dup@it-test.com",
              "ownerName": "Owner Dup",
              "ownerPassword": "Senha@123"
            }
        """;

        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /admin/auth/login com SUPER_ADMIN → 200 sem token no body")
    void adminLogin_withSuperAdmin_shouldSetCookieNotReturnToken()
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "superadmin@dipdv.app",
                      "password": "SuperAdmin@2025!"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
            .andExpect(header().exists("Set-Cookie"));
    }
}
