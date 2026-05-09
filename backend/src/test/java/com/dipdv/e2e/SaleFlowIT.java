package com.dipdv.e2e;

import com.dipdv.support.IntegrationTest;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
 * Teste E2E do fluxo completo de venda:
 * Caixa aberto → Produto criado → Pedido criado → Item adicionado
 * → Pedido fechado → Pagamento → Estoque abatido → NFC-e gerada
 */
@IntegrationTest
@AutoConfigureMockMvc
class SaleFlowIT extends PostgresIntegrationSupport {

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0001-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    String adminToken;
    String cashierToken;

    @BeforeEach
    void setUp() {
        adminToken   = "Bearer " + jwtService.generateToken(USER_ID, TENANT_ID, "ADMIN");
        cashierToken = "Bearer " + jwtService.generateToken(USER_ID, TENANT_ID, "CASHIER");

        // Garantir tenant de teste existe
        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_ID.toString(), "Lanchonete Dev", "dev-tenant");

        // Fechar qualquer caixa aberto de testes anteriores
        jdbc.execute("SET app.current_tenant = '" + TENANT_ID + "'");
        jdbc.update(
            "UPDATE cash_registers SET status = 'CLOSED' " +
            "WHERE tenant_id = ?::uuid AND status = 'OPEN'",
            TENANT_ID.toString());

        // Cancelar pedidos abertos
        jdbc.update(
            "UPDATE orders SET status = 'CANCELED', cancel_reason = 'Cleanup for test' " +
            "WHERE tenant_id = ?::uuid AND status IN ('OPEN')",
            TENANT_ID.toString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result));
    }

    String id(MvcResult result) throws Exception {
        return json(result).get("id").asText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTE PRINCIPAL — fluxo completo em sequência
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fluxo completo: caixa → produto → pedido → pagamento → NFC-e")
    void fullSaleFlow() throws Exception {

        // ── 1. Abrir caixa ────────────────────────────────────────────────
        MvcResult cashResult = mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 100.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();

        String cashRegisterId = id(cashResult);
        assertThat(cashRegisterId).isNotBlank();

        // ── 2. Criar categoria ────────────────────────────────────────────
        MvcResult catResult = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Lanches E2E\", \"icon\": \"hamburger\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        String categoryId = id(catResult);

        // ── 3. Criar produto com estoque ──────────────────────────────────
        MvcResult prodResult = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "X-Burguer E2E",
                      "price": 18.90,
                      "stockQuantity": 10,
                      "stockMinLevel": 2,
                      "categoryId": "%s"
                    }
                """.formatted(categoryId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stockQuantity").value(10))
            .andReturn();

        String productId = id(prodResult);

        // ── 4. Criar pedido ───────────────────────────────────────────────
        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.total").value(0))
            .andReturn();

        String orderId = id(orderResult);

        // ── 5. Adicionar item ao pedido ───────────────────────────────────
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "modifiers": []
                    }
                """.formatted(productId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(37.80));

        // ── 6. Fechar pedido ──────────────────────────────────────────────
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));

        // ── 7. Registrar pagamento CASH ───────────────────────────────────
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": "%s",
                      "method": "CASH",
                      "amount": 40.00,
                      "idempotencyKey": "%s"
                    }
                """.formatted(orderId, idempotencyKey)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.changeAmount").value(2.20))
            .andReturn();

        String paymentId = id(payResult);

        // ── 8. Verificar idempotência ─────────────────────────────────────
        MvcResult dupResult = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": "%s",
                      "method": "CASH",
                      "amount": 40.00,
                      "idempotencyKey": "%s"
                    }
                """.formatted(orderId, idempotencyKey)))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(id(dupResult)).isEqualTo(paymentId); // mesmo UUID

        // ── 9. Verificar estoque abatido ──────────────────────────────────
        MvcResult prodAfter = mockMvc.perform(get("/api/v1/products/" + productId)
                .header("Authorization", adminToken))
            .andExpect(status().isOk())
            .andReturn();

        int stockAfter = json(prodAfter).get("stockQuantity").asInt();
        assertThat(stockAfter).isEqualTo(8); // 10 - 2

        // ── 10. Verificar NFC-e emitida ───────────────────────────────────
        mockMvc.perform(get("/api/v1/nfce/order/" + orderId)
                .header("Authorization", cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"))
            .andExpect(result -> {
                String accessKey = json(result).get("accessKey").asText();
                assertThat(accessKey).hasSize(44);
                assertThat(accessKey).matches("\\d{44}");
            });

        // ── 11. Fechar caixa ──────────────────────────────────────────────
        mockMvc.perform(patch("/api/v1/cash-registers/" + cashRegisterId + "/close")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"physicalBalance\": 137.80}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.totalCash").value(40.00))
            .andExpect(jsonPath("$.closingBalance").value(140.00))
            .andExpect(jsonPath("$.difference").value(-2.20));

        // ── 12. Verificar audit_log ───────────────────────────────────────
        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE tenant_id = ?::uuid AND action = 'CASH_REGISTER_CLOSED'",
            Integer.class, TENANT_ID.toString());

        assertThat(auditCount).isGreaterThan(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTES COMPLEMENTARES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Pagamento sem caixa aberto → 409")
    void payment_withoutOpenCashRegister_shouldReturn409() throws Exception {
        // Criar e fechar pedido
        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();

        String orderId = id(orderResult);

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", cashierToken))
            .andExpect(status().isOk());

        // Tentar pagar sem caixa aberto
        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": "%s",
                      "method": "CASH",
                      "amount": 10.00,
                      "idempotencyKey": "%s"
                    }
                """.formatted(orderId, UUID.randomUUID())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Pagamento PIX excedendo o total → 409")
    void payment_pixExceedingTotal_shouldReturn409() throws Exception {
        // Abrir caixa
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 0}"))
            .andExpect(status().isCreated());

        // Criar pedido com produto
        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();

        String orderId = id(orderResult);

        // Criar categoria e produto
        MvcResult catResult = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Categoria PIX Test\", \"position\": 99}"))
            .andReturn();
        String catId = id(catResult);

        MvcResult prodResult = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Produto PIX","price":10.00,
                     "stockQuantity":5,"stockMinLevel":1,
                     "categoryId":"%s"}
                """.formatted(catId)))
            .andReturn();
        String prodId = id(prodResult);

        // Adicionar item e fechar
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","quantity":1,"modifiers":[]}
                """.formatted(prodId)))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/close")
                .header("Authorization", cashierToken))
            .andExpect(status().isOk());

        // Pagar PIX com valor maior que o total → 409
        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": "%s",
                      "method": "PIX",
                      "amount": 20.00,
                      "idempotencyKey": "%s"
                    }
                """.formatted(orderId, UUID.randomUUID())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Dois caixas abertos simultâneos → 409")
    void openCashRegister_twice_shouldReturn409() throws Exception {
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 50.00}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 50.00}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }
}
