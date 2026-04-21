package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class OrderControllerIT extends ControllerIntegrationSupport {

    @Test
    @DisplayName("POST /orders com CASHIER → 201 com status OPEN")
    void createOrder_withCashier_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.version").value(0));
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
    @DisplayName("PATCH /orders/{id}/cancel com CASHIER → 403")
    void cancelOrder_withCashier_shouldReturn403() throws Exception {
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
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /orders com CASHIER → 403 (apenas MANAGER+)")
    void listOrders_withCashier_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                .header("Authorization", tokenFor("CASHIER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /orders com MANAGER → 200 paginado")
    void listOrders_withManager_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                .header("Authorization", tokenFor("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }
}
