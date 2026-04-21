package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class CatalogControllerIT extends ControllerIntegrationSupport {

    // ── Categories ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /categories → 200 com lista paginada")
    void listCategories_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /categories com ADMIN → 201")
    void createCategory_withAdmin_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Categoria IT Test", "position": 1}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Categoria IT Test"));
    }

    @Test
    @DisplayName("POST /categories com nome duplicado → 409")
    void createCategory_withDuplicateName_shouldReturn409() throws Exception {
        String body = """
            {"name": "Categoria Duplicada IT", "position": 1}
        """;

        // Criar primeira vez
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        // Tentar criar novamente com mesmo nome
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /categories com payload vazio → 400 com campos")
    void createCategory_withEmptyPayload_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields").isArray());
    }

    @Test
    @DisplayName("GET /categories/{id} inexistente → 404")
    void getCategory_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/00000000-0000-0000-0000-999999999999")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── Products ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /products → 200 com lista paginada")
    void listProducts_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /products com ADMIN → 201")
    void createProduct_withAdmin_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Produto IT Test",
                      "price": 19.90,
                      "stockQuantity": 10,
                      "stockMinLevel": 2
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.price").value(19.90));
    }

    @Test
    @DisplayName("DELETE /products/{id} com ADMIN → 204 (soft delete)")
    void deleteProduct_withAdmin_shouldReturn204() throws Exception {
        // Criar produto
        var result = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", tokenFor("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Produto Para Deletar IT", "price": 9.90,
                     "stockQuantity": 5, "stockMinLevel": 1}
                """))
            .andExpect(status().isCreated())
            .andReturn();

        String id = objectMapper.readTree(
            result.getResponse().getContentAsString()).get("id").asText();

        // Deletar (soft delete)
        mockMvc.perform(delete("/api/v1/products/" + id)
                .header("Authorization", tokenFor("ADMIN")))
            .andExpect(status().isNoContent());

        // Verificar que não aparece mais na listagem
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", tokenFor("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(result2 -> {
                String body2 = result2.getResponse().getContentAsString();
                org.junit.jupiter.api.Assertions.assertFalse(
                    body2.contains("Produto Para Deletar IT"));
            });
    }
}
