package com.dipdv.modules.catalog.controller;

import com.dipdv.modules.catalog.dto.category.CategoryRequest;
import com.dipdv.shared.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Este teste visa provar a Integração ponta-a-ponta focada em Segurança.
 * Usa o próprio `JwtService` para gerar tokens criptografados reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev") // Foca nas credenciais contidas no banco docker localhost
@Transactional // Causa rollback dos testes ao invés de sujar o dev database
class CategoryControllerSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    // Tenant Base da Documentação/Initial Schema
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createCategory_WithoutToken_ShouldReturn401() throws Exception {
        CategoryRequest request = new CategoryRequest("Bebidas Seguranca", 1, true);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // HTTP 401
    }

    @Test
    void createCategory_WithCashierToken_ShouldReturn403() throws Exception {
        // Gera um token validado localmente porém simulando um Caixa tentando criar uma categoria
        String cashierToken = jwtService.generateToken(UUID.randomUUID(), TENANT_ID, "CASHIER");
        CategoryRequest request = new CategoryRequest("Bebidas Seguranca", 1, true);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()); // HTTP 403
    }

    @Test
    void createCategory_WithAdminToken_ShouldReturn201() throws Exception {
        // Gera um token verdadeiro para um ADMIN
        String adminToken = jwtService.generateToken(UUID.randomUUID(), TENANT_ID, "ADMIN");
        CategoryRequest request = new CategoryRequest("Doces Seguranca", 10, true);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()); // HTTP 201
    }
}
