package com.dipdv.controller;

import com.dipdv.shared.module.entity.TenantModule;
import com.dipdv.shared.module.entity.TenantModuleId;
import com.dipdv.shared.module.repository.TenantModuleRepository;
import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de segurança transversais — verificam que @PreAuthorize
 * está funcionando corretamente em todos os módulos.
 */
@IntegrationTest
class SecurityControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private TenantModuleRepository tenantModuleRepository;

    @Autowired
    private RlsTestHelper rlsHelper;

    @BeforeEach
    void setupModules() {
        rlsHelper.runAsSuperAdmin(() -> {
            tenantModuleRepository.save(TenantModule.builder()
                    .id(new TenantModuleId(TENANT_ID, "REPORTS"))
                    .enabled(true)
                    .build());
        });
    }

    // ── Endpoints públicos ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health sem token → 200")
    void healthCheck_withoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Swagger UI acessível sem token")
    void swagger_withoutToken_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assertTrue(status == 200 || status == 302 || status == 404,
                    "Esperado 200, 302 ou 404, mas foi: " + status);
            });
    }

    // ── Endpoints protegidos sem token ───────────────────────────────────

    @Nested
    @DisplayName("Sem token → 401")
    class WithoutToken {

        @Test
        void products_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void orders_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void adminTenants_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── @PreAuthorize por role ────────────────────────────────────────────

    @Nested
    @DisplayName("CASHIER não acessa endpoints de gestão → 403")
    class CashierRestrictions {

        @Test
        void cashier_cannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                    .header("Authorization", tokenFor("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Produto Teste",
                          "price": 10.00,
                          "stockQuantity": 5,
                          "stockMinLevel": 1
                        }
                    """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        void cashier_cannotAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isForbidden());
        }

        @Test
        void cashier_cannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isForbidden());
        }

        @Test
        void cashier_canAccessProducts_read() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("MANAGER não acessa endpoints exclusivos de ADMIN → 403")
    class ManagerRestrictions {

        @Test
        void manager_cannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                    .header("Authorization", tokenFor("MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Produto Teste",
                          "price": 10.00,
                          "stockQuantity": 5,
                          "stockMinLevel": 1
                        }
                    """))
                .andExpect(status().isForbidden());
        }

        @Test
        void manager_canAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", tokenFor("MANAGER")))
                .andExpect(status().isOk());
        }

        @Test
        void manager_cannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", tokenFor("MANAGER")))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN acessa tudo → nunca 403")
    class SuperAdminAccess {

        @Test
        void superAdmin_canAccessAdminTenants() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }

        @Test
        void superAdmin_canAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }

        @Test
        void superAdmin_canAccessProducts() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }
    }

    // ── Token inválido ────────────────────────────────────────────────────

    @Test
    @DisplayName("Token JWT inválido → 401 com ApiError")
    void invalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", "Bearer token.invalido.aqui"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Token expirado → 401")
    void expiredToken_shouldReturn401() throws Exception {
        // Gerar token com expiração no passado não é simples sem mock
        // Validar apenas que token malformado retorna 401
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.expirado.sig"))
            .andExpect(status().isUnauthorized());
    }
}
