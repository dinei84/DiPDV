package com.dipdv.controller;

import com.dipdv.modules.cashregister.repository.CashRegisterRepository;
import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class OrderControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CashRegisterRepository cashRegisterRepository;

    @BeforeEach
    void setUp() {
        // Garantir tenant de teste existe e setar contexto para JdbcTemplate (RLS)
        jdbcTemplate.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_ID.toString(), "Tenant Teste", "tenant-teste");
        
        jdbcTemplate.execute("SET app.current_tenant = '" + TENANT_ID + "'");

        // Limpar dados para cada teste para garantir isolamento
        jdbcTemplate.execute("DELETE FROM stock_movements");
        jdbcTemplate.execute("DELETE FROM nfce_documents");
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM cash_registers");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM categories");
    }

    private void openCashRegister() throws Exception {
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 100.00}"))
            .andExpect(status().isCreated());
    }

    private String createProduct(String name, double price) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO products (id, tenant_id, name, price, stock_quantity, stock_min_level, category_id, created_at) " +
            "VALUES (?, ?, ?, ?, 100, 10, null, now())",
            id, TENANT_ID, name, price
        );
        return id.toString();
    }

    @Test
    @DisplayName("POST /orders com CASHIER → 201 com status OPEN")
    void createOrder_withCashier_shouldReturn201() throws Exception {
        openCashRegister();
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.cashRegisterId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /orders/{id} inexistente → 404")
    void getOrder_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-999999999999")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /orders/{id}/cancel sem motivo → 400")
    void cancelOrder_withoutReason_shouldReturn400() throws Exception {
        openCashRegister();
        // Criar pedido
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(
            result.getResponse().getContentAsString()).get("id").asText();

        // Tentar cancelar sem motivo
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", tokenFor("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /orders/{id}/cancel com CASHIER → 200")
    void cancelOrder_withCashier_shouldReturn200() throws Exception {
        openCashRegister();
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(
            result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"teste\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders com CASHIER → 200")
    void listOrders_withCashier_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /orders com MANAGER → 200 paginado")
    void listOrders_withManager_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                .header("Authorization", tokenFor("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    // --- Novos Testes Sprint 5.2.1 ---

    @Test
    @DisplayName("POST /orders sem caixa aberto → 409")
    void createOrder_withoutOpenCashRegister_shouldReturn409() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Não há caixa aberto para iniciar pedidos"));
    }

    @Test
    @DisplayName("POST /orders com identifier 'Mesa 1' → 201")
    void createOrder_withIdentifier_shouldReturn201() throws Exception {
        openCashRegister();
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.identifier").value("Mesa 1"));
    }

    @Test
    @DisplayName("POST /orders com identifier duplicado enquanto aberto → 409")
    void createOrder_withDuplicateIdentifier_shouldReturn409() throws Exception {
        openCashRegister();
        // Primeiro pedido Mesa 1
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andExpect(status().isCreated());

        // Segundo pedido Mesa 1 (duplicado e aberto)
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Já existe um pedido aberto com o identificador: Mesa 1"));
    }

    @Test
    @DisplayName("POST /orders permite reuso de identifier após cancelamento → 201")
    void createOrder_reuseIdentifierAfterCancel_shouldReturn201() throws Exception {
        openCashRegister();
        // Criar e cancelar Mesa 1
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", tokenFor("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"erro\"}"))
            .andExpect(status().isOk());

        // Reusar Mesa 1
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.identifier").value("Mesa 1"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/close com caixa fechado → 409")
    void closeOrder_withClosedCashRegister_shouldReturn409() throws Exception {
        openCashRegister();
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Fechar o caixa
        jdbcTemplate.execute("UPDATE cash_registers SET status = 'CLOSED', closed_at = now()");

        // Tentar fechar o pedido
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Caixa associado ao pedido já foi fechado"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/cancel com payment PAID → 409")
    void cancelOrder_withPaidPayment_shouldReturn409() throws Exception {
        openCashRegister();
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        UUID registerId = jdbcTemplate.queryForObject("SELECT id FROM cash_registers WHERE status = 'OPEN' LIMIT 1", UUID.class);

        // Inserir um pagamento PAID manualmente
        jdbcTemplate.update(
            "INSERT INTO payments (id, tenant_id, order_id, amount, method, status, created_at, idempotency_key, cash_register_id) " +
            "VALUES (?, ?, ?, 10.00, 'CASH', 'PAID', now(), ?, ?)",
            UUID.randomUUID(), TENANT_ID, UUID.fromString(orderId), UUID.randomUUID().toString(), registerId
        );

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", tokenFor("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"desistência\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("Estorne os pagamentos primeiro")));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/cancel sem payments → 200")
    void cancelOrder_withoutPayments_shouldReturn200() throws Exception {
        openCashRegister();
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", tokenFor("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"erro no pedido\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/close → 200")
    void closeOrder_whenValid_shouldReturn200() throws Exception {
        openCashRegister();
        var result = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/items/{itemId} → 200 e total recalculado")
    void updateItemQuantity_shouldUpdateTotal() throws Exception {
        openCashRegister();
        String productId = createProduct("Coca", 5.00);

        var orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();
        String orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString()).get("id").asText();

        var itemResult = mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + productId + "\", \"quantity\": 1, \"modifiers\": []}"))
            .andReturn();
        String itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString()).get("items").get(0).get("id").asText();

        // Update para qty 3
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/items/" + itemId)
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(15.00))
            .andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.items[0].totalPrice").value(15.00));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/items/{itemId} em pedido CLOSED → 409")
    void updateItemQuantity_whenClosed_shouldReturn409() throws Exception {
        openCashRegister();
        String productId = createProduct("Coca", 5.00);

        var orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andReturn();
        String orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString()).get("id").asText();

        var itemResult = mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + productId + "\", \"quantity\": 1, \"modifiers\": []}"))
            .andReturn();
        String itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString()).get("items").get(0).get("id").asText();

        // Fechar pedido
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk());

        // Tentar update
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/items/" + itemId)
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 5}"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /orders/{id}/items/{itemId} com qty 0 → 400")
    void updateItemQuantity_withZero_shouldReturn400() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/" + UUID.randomUUID() + "/items/" + UUID.randomUUID())
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /orders?status=OPEN inclui identifier (preenchido)")
    void listOrders_shouldIncludeIdentifier() throws Exception {
        openCashRegister();

        // Criar comanda com identifier "Mesa 1"
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"Mesa 1\"}"))
            .andExpect(status().isCreated());

        // Listar todas as OPEN
        mockMvc.perform(get("/api/v1/orders?status=OPEN")
                .header("Authorization", tokenFor("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0]").exists())
            .andExpect(jsonPath("$.content[0].identifier").value("Mesa 1"))
            .andExpect(jsonPath("$.content[0].itemCount").value(0));
    }
}
