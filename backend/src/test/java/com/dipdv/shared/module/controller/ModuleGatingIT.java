package com.dipdv.shared.module.controller;

import com.dipdv.shared.module.entity.TenantModule;
import com.dipdv.shared.module.entity.TenantModuleId;
import com.dipdv.shared.module.repository.TenantModuleRepository;
import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@Transactional
class ModuleGatingIT extends ControllerIntegrationSupport {

    @Autowired
    private TenantModuleRepository tenantModuleRepository;

    @Autowired
    private RlsTestHelper rlsHelper;

    @Test
    @DisplayName("GET /api/v1/reports/summary sem módulo REPORTS deve retornar 403")
    void shouldReturn403WhenModuleNotEnabled() throws Exception {
        // Garante que o módulo REPORTS está desabilitado para o tenant de teste
        rlsHelper.runAsSuperAdmin(() -> tenantModuleRepository.deleteAll());

        mockMvc.perform(get("/api/v1/reports/summary")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MODULE_NOT_ENABLED"))
                .andExpect(jsonPath("$.module").value("REPORTS"));
    }

    @Test
    @DisplayName("GET /api/v1/reports/summary com módulo REPORTS habilitado deve retornar 200")
    void shouldReturn200WhenModuleEnabled() throws Exception {
        // Habilita módulo REPORTS para o tenant de teste
        rlsHelper.runAsSuperAdmin(() -> {
            tenantModuleRepository.save(TenantModule.builder()
                    .id(new TenantModuleId(TENANT_ID, "REPORTS"))
                    .enabled(true)
                    .build());
        });

        mockMvc.perform(get("/api/v1/reports/summary")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPER_ADMIN deve bypassar o gating de módulos")
    void superAdminShouldBypassGating() throws Exception {
        // Garante que o módulo REPORTS está desabilitado
        rlsHelper.runAsSuperAdmin(() -> tenantModuleRepository.deleteAll());

        mockMvc.perform(get("/api/v1/reports/summary")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
