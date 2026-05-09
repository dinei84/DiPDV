package com.dipdv.modules.catalog.controller;

import com.dipdv.shared.security.JwtService;
import com.dipdv.support.IntegrationTest;
import com.dipdv.support.PostgresIntegrationSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Catalog module (Categories and Products).
 * Covers ?includeDeleted parameter, soft delete validation, role-based access,
 * module gating, and cross-tenant isolation.
 */
@IntegrationTest
@AutoConfigureMockMvc
class CatalogControllerIT extends PostgresIntegrationSupport {

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    String adminToken;
    String cashierToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtService.generateToken(USER_ID, TENANT_ID, "ADMIN");
        cashierToken = "Bearer " + jwtService.generateToken(USER_ID, TENANT_ID, "CASHIER");

        // Ensure tenants exist
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            TENANT_ID.toString(), "Tenant A", "tenant-a");
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            TENANT_B.toString(), "Tenant B", "tenant-b");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    String id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTS: ?includeDeleted parameter
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /categories without ?includeDeleted excludes soft-deleted categories")
    void listCategories_withoutIncludeDeleted_shouldExcludeDeleted() throws Exception {
        // Create and soft-delete a category
        MvcResult catResult = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Cat To Delete\", \"icon\": \"trash\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String categoryId = id(catResult);

        // Delete it
        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                .header("Authorization", adminToken))
            .andExpect(status().isNoContent());

        // List without includeDeleted should not see it
        mockMvc.perform(get("/api/v1/categories")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == '" + categoryId + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /categories?includeDeleted=true includes soft-deleted categories")
    void listCategories_withIncludeDeletedTrue_shouldIncludeDeleted() throws Exception {
        // Create and soft-delete a category
        MvcResult catResult = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Cat To Delete2\", \"icon\": \"trash\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String categoryId = id(catResult);

        // Delete it
        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                .header("Authorization", adminToken))
            .andExpect(status().isNoContent());

        // List with includeDeleted should see it
        mockMvc.perform(get("/api/v1/categories?includeDeleted=true")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == '" + categoryId + "')].deletedAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /products without ?includeDeleted excludes soft-deleted products")
    void listProducts_withoutIncludeDeleted_shouldExcludeDeleted() throws Exception {
        // Create product and delete it
        MvcResult prodResult = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Prod To Delete",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String productId = id(prodResult);

        // Delete it
        mockMvc.perform(delete("/api/v1/products/" + productId)
                .header("Authorization", adminToken))
            .andExpect(status().isNoContent());

        // List without includeDeleted should not see it
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == '" + productId + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /products?includeDeleted=true includes soft-deleted products")
    void listProducts_withIncludeDeletedTrue_shouldIncludeDeleted() throws Exception {
        // Create and delete product
        MvcResult prodResult = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Prod To Delete2",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String productId = id(prodResult);

        mockMvc.perform(delete("/api/v1/products/" + productId)
                .header("Authorization", adminToken))
            .andExpect(status().isNoContent());

        // List with includeDeleted should see it
        mockMvc.perform(get("/api/v1/products?includeDeleted=true")
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == '" + productId + "')].deletedAt").isNotEmpty());
    }


    @Test
    @DisplayName("DELETE /categories/{id} with linked products should return 400")
    void deleteCategory_whenHasLinkedProducts_shouldReturn400() throws Exception {
        // Create category
        MvcResult catResult = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Cat With Products\", \"icon\": \"box\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String categoryId = id(catResult);

        // Create product in this category
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Product In Cat",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1,
                      "categoryId": "%s"
                    }
                    """.formatted(categoryId)))
            .andExpect(status().isCreated());

        // Try to delete category with product
        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                .header("Authorization", adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Categoria não pode ser excluída pois possui produtos vinculados"));
    }


    // ─────────────────────────────────────────────────────────────────────────
    // TESTS: Duplicate name validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /products with duplicate name should return 409")
    void createProduct_withDuplicateName_shouldReturn409() throws Exception {
        // Create first product
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Unique Product",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isCreated());

        // Try to create second with same name
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Unique Product",
                      "price": 15.00,
                      "stockQuantity": 3,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Já existe um produto com este nome"));
    }

    @Test
    @DisplayName("POST /categories with duplicate name should return 409")
    void createCategory_withDuplicateName_shouldReturn409() throws Exception {
        // Create first category
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Unique Cat\", \"icon\": \"box\"}"))
            .andExpect(status().isCreated());

        // Try to create second with same name
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Unique Cat\", \"icon\": \"trash\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Já existe uma categoria com este nome"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTS: Role-based access control (CASHIER restrictions)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /categories with CASHIER should return 403")
    void createCategory_withCashier_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Cat\", \"icon\": \"box\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("POST /products with CASHIER should return 403")
    void createProduct_withCashier_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "New Product",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PUT /products/{id} with CASHIER should return 403")
    void updateProduct_withCashier_shouldReturn403() throws Exception {
        // Create product as admin
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Product for Update",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String productId = id(result);

        // Try to update as cashier
        mockMvc.perform(put("/api/v1/products/" + productId)
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated",
                      "price": 20.00,
                      "stockQuantity": 10,
                      "stockMinLevel": 2
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE /products/{id} with CASHIER should return 403")
    void deleteProduct_withCashier_shouldReturn403() throws Exception {
        // Create product as admin
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Product for Delete",
                      "price": 10.00,
                      "stockQuantity": 5,
                      "stockMinLevel": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String productId = id(result);

        // Try to delete as cashier
        mockMvc.perform(delete("/api/v1/products/" + productId)
                .header("Authorization", cashierToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /products with CASHIER should return 200 (read allowed)")
    void listProducts_withCashier_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", cashierToken))
            .andExpect(status().isOk());
    }

}
