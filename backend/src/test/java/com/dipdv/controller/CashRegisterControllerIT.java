package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class CashRegisterControllerIT extends ControllerIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void closePreviousCashRegister() {
        jdbcTemplate.update(
            "UPDATE cash_registers SET status = 'CLOSED' " +
            "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid " +
            "AND status = 'OPEN'"
        );
    }

    @Test
    @DisplayName("POST /cash-registers com CASHIER → 201 OPEN")
    void openCashRegister_withCashier_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 100.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.openingBalance").value(100.00));
    }

    @Test
    @DisplayName("POST /cash-registers gera audit_log CASH_REGISTER_OPENED")
    void openCashRegister_shouldCreateAuditLog() throws Exception {
        var result = mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 100.00}"))
            .andExpect(status().isCreated())
            .andReturn();

        String id = objectMapper.readTree(
            result.getResponse().getContentAsString()).get("id").asText();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log " +
            "WHERE tenant_id = ?::uuid AND action = 'CASH_REGISTER_OPENED' " +
            "AND entity = 'cash_registers' AND entity_id = ?::uuid",
            Integer.class,
            TENANT_ID.toString(),
            id
        );

        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    @DisplayName("POST /cash-registers com caixa já aberto → 409")
    void openCashRegister_whenAlreadyOpen_shouldReturn409() throws Exception {
        // Abrir primeiro caixa
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 50.00}"))
            .andExpect(status().isCreated());

        // Tentar abrir segundo caixa
        mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 50.00}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PATCH /cash-registers/{id}/close com CASHIER → 403")
    void closeCashRegister_withCashier_shouldReturn403() throws Exception {
        // Abrir caixa
        var result = mockMvc.perform(post("/api/v1/cash-registers")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingBalance\": 0}"))
            .andReturn();

        String id = objectMapper.readTree(
            result.getResponse().getContentAsString()).get("id").asText();

        // Tentar fechar com CASHIER
        mockMvc.perform(patch("/api/v1/cash-registers/" + id + "/close")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"physicalBalance\": 0}"))
            .andExpect(status().isForbidden());
    }
}
