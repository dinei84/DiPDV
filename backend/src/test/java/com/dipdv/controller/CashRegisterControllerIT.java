package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class CashRegisterControllerIT extends ControllerIntegrationSupport {

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
