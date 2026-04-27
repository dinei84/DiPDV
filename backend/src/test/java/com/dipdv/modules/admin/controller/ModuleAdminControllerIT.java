package com.dipdv.modules.admin.controller;

import com.dipdv.shared.module.repository.TenantModuleRepository;
import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class ModuleAdminControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private TenantModuleRepository tenantModuleRepository;

    @Test
    @DisplayName("SUPER_ADMIN consegue habilitar e desabilitar módulos")
    void superAdminShouldEnableAndDisableModules() throws Exception {
        String moduleCode = "REPORTS";

        // 1. Habilitar
        mockMvc.perform(post("/api/v1/admin/modules/tenants/{tenantId}/enable", TENANT_ID)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", moduleCode))))
                .andExpect(status().isOk());

        assertTrue(tenantModuleRepository.isModuleEnabledForTenant(TENANT_ID, moduleCode));

        // 2. Desabilitar
        mockMvc.perform(post("/api/v1/admin/modules/tenants/{tenantId}/disable", TENANT_ID)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", moduleCode))))
                .andExpect(status().isOk());

        assertFalse(tenantModuleRepository.isModuleEnabledForTenant(TENANT_ID, moduleCode));
    }

    @Test
    @DisplayName("ADMIN comum não acessa endpoints de admin de módulos")
    void commonAdminShouldNotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules/catalog")
                .header("Authorization", tokenFor("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tentar desabilitar módulo BASE deve retornar 400")
    void shouldReturn400WhenDisablingBaseModule() throws Exception {
        mockMvc.perform(post("/api/v1/admin/modules/tenants/{tenantId}/disable", TENANT_ID)
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", "PDV_BASIC"))))
                .andExpect(status().isBadRequest());
    }
}
