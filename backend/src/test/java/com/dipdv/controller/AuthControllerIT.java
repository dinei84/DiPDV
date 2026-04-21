package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class AuthControllerIT extends ControllerIntegrationSupport {

    static final String LOGIN_URL = "/api/v1/auth/login";

    @Test
    @DisplayName("POST /auth/login com credenciais válidas → 200 com token")
    void login_withValidCredentials_shouldReturn200() throws Exception {
        // O DataInitializer cria admin@dipdv.dev no perfil test
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "00000000-0000-0000-0000-000000000001",
                      "email": "admin@dipdv.dev",
                      "password": "dipdv@2025"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.tenantId")
                .value("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("POST /auth/login com senha errada → 401 com ApiError")
    void login_withWrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "00000000-0000-0000-0000-000000000001",
                      "email": "admin@dipdv.dev",
                      "password": "senha-errada"
                    }
                """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login com payload inválido → 400 com campos")
    void login_withInvalidPayload_shouldReturn400WithFields() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": ""}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fields").isArray())
            .andExpect(jsonPath("$.fields.length()").value(3));
    }

    @Test
    @DisplayName("POST /auth/login sem Content-Type → 415")
    void login_withoutContentType_shouldReturn415() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .content("{\"email\":\"a@b.com\"}"))
            .andExpect(status().isUnsupportedMediaType());
    }
}
