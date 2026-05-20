package com.dipdv.modules.admin.controller;

import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.support.PostgresIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AdminControllerSecurityTest extends PostgresIntegrationSupport {

    private static final UUID DEV_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminLogin_shouldSetHttpOnlyCookieAndAuthorizeProtectedEndpoint() throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "superadmin@dipdv.app",
                "password", "SuperAdmin@2025!"
        ));

        var loginResult = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.name").value("Super Admin DiPDV"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().exists("dipdv_admin_token"))
                .andExpect(cookie().httpOnly("dipdv_admin_token", true))
                .andExpect(cookie().secure("dipdv_admin_token", false))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andReturn();

        Cookie adminCookie = loginResult.getResponse().getCookie("dipdv_admin_token");

        mockMvc.perform(get("/api/v1/admin/tenants").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void adminTenants_withRegularAdminBearerToken_shouldReturn403() throws Exception {
        String userLoginBody = objectMapper.writeValueAsString(new LoginRequest(
                "admin@dipdv.dev",
                "dipdv@2025"
        ));

        String authBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userLoginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String adminToken = objectMapper.readValue(authBody, AuthResponse.class).token();

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
