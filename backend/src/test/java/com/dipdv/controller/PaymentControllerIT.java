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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class PaymentControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CashRegisterRepository cashRegisterRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_ID.toString(), "Tenant Teste", "tenant-teste");

        jdbcTemplate.execute("SET app.current_tenant = '" + TENANT_ID + "'");

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
    @DisplayName("POST /payments gera audit_log PAYMENT_REGISTERED")
    void registerPayment_shouldCreateAuditLog() throws Exception {
        openCashRegister();
        String productId = createProduct("Café", 5.00);

        // Criar pedido
        var orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();

        String orderId = objectMapper.readTree(
            orderResult.getResponse().getContentAsString()).get("id").asText();

        // Adicionar item ao pedido
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": \"" + productId + "\", \"quantity\": 1}"))
            .andExpect(status().isOk());

        // Fechar pedido
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isOk());

        // Registrar pagamento
        String idempotencyKey = UUID.randomUUID().toString();
        var paymentResult = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" +
                    "\"orderId\": \"" + orderId + "\", " +
                    "\"method\": \"CASH\", " +
                    "\"amount\": \"5.00\", " +
                    "\"idempotencyKey\": \"" + idempotencyKey + "\"" +
                    "}"))
            .andExpect(status().isCreated())
            .andReturn();

        String paymentId = objectMapper.readTree(
            paymentResult.getResponse().getContentAsString()).get("id").asText();

        // Verificar audit_log
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE tenant_id = ?::uuid AND action = 'PAYMENT_REGISTERED' " +
            "AND entity = 'payments' AND entity_id = ?::uuid",
            Integer.class,
            TENANT_ID.toString(),
            paymentId
        );

        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }
}
