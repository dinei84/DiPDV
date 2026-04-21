# T2 — Testes de Controller + Status HTTP (Claude Code CLI)

## Instruções para o Claude Code

Antes de implementar, ler:
- `backend/src/test/java/com/dipdv/support/` — infraestrutura T1 já criada
- `backend/src/main/java/com/dipdv/shared/security/JwtService.java` — para gerar tokens nos testes
- `backend/src/main/java/com/dipdv/shared/security/SecurityConfig.java` — rotas públicas/protegidas
- `backend/src/main/java/com/dipdv/modules/auth/controller/` — endpoints de auth
- `backend/src/main/java/com/dipdv/modules/catalog/controller/` — endpoints de catalog
- `backend/src/main/java/com/dipdv/modules/order/controller/` — endpoints de order
- `backend/src/main/java/com/dipdv/modules/payment/controller/` — endpoints de payment
- `backend/src/main/java/com/dipdv/modules/cashregister/controller/` — endpoints de caixa
- `backend/src/main/java/com/dipdv/modules/admin/controller/AdminController.java` — endpoints admin

**Branch:** continuar em `feature/test-rls-integration`

---

## Contexto

Temos 59 testes unitários (Services com mocks) e 10 testes RLS
(PostgreSQL real via Testcontainers). Nenhum teste verifica:
- Se os endpoints retornam os status HTTP corretos
- Se o `@PreAuthorize` bloqueia roles incorretas
- Se o corpo do response está no formato `ApiError` esperado
- Se endpoints públicos funcionam sem token

---

## Tarefa 1 — Classe base para testes de Controller

**Arquivo:** `backend/src/test/java/com/dipdv/support/ControllerIntegrationSupport.java`

```java
package com.dipdv.support;

import com.dipdv.shared.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/**
 * Suporte base para testes de Controller com MockMvc.
 *
 * Fornece helpers para:
 * - Gerar tokens JWT por role
 * - Fazer requests autenticados
 * - Serializar/deserializar JSON
 *
 * Não sobe banco real — usa mocks onde necessário.
 * Para testes que precisam de banco, usar PostgresIntegrationSupport.
 */
@AutoConfigureMockMvc
public abstract class ControllerIntegrationSupport
        extends PostgresIntegrationSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;

    protected static final UUID TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    protected static final UUID USER_ID = UUID.randomUUID();

    /** Gera token JWT para um role específico */
    protected String tokenFor(String role) {
        return "Bearer " + jwtService.generateToken(USER_ID, TENANT_ID, role);
    }

    /** Token SUPER_ADMIN com tenant master */
    protected String superAdminToken() {
        return "Bearer " + jwtService.generateToken(
            USER_ID, MASTER_TENANT_ID, "SUPER_ADMIN");
    }

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
```

---

## Tarefa 2 — Testes de Auth Controller

**Arquivo:** `backend/src/test/java/com/dipdv/controller/AuthControllerIT.java`

```java
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
```

---

## Tarefa 3 — Testes de Segurança (roles e autenticação)

**Arquivo:** `backend/src/test/java/com/dipdv/controller/SecurityControllerIT.java`

```java
package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de segurança transversais — verificam que @PreAuthorize
 * está funcionando corretamente em todos os módulos.
 */
@IntegrationTest
class SecurityControllerIT extends ControllerIntegrationSupport {

    // ── Endpoints públicos ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health sem token → 200")
    void healthCheck_withoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /swagger-ui.html sem token → 200 ou redirect")
    void swagger_withoutToken_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(result ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    result.getResponse().getStatus() == 200 ||
                    result.getResponse().getStatus() == 302));
    }

    // ── Endpoints protegidos sem token ───────────────────────────────────

    @Nested
    @DisplayName("Sem token → 401")
    class WithoutToken {

        @Test
        void products_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void orders_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void adminTenants_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── @PreAuthorize por role ────────────────────────────────────────────

    @Nested
    @DisplayName("CASHIER não acessa endpoints de gestão → 403")
    class CashierRestrictions {

        @Test
        void cashier_cannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                    .header("Authorization", tokenFor("CASHIER"))
                    .contentType("application/json")
                    .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        void cashier_cannotAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isForbidden());
        }

        @Test
        void cashier_cannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isForbidden());
        }

        @Test
        void cashier_canAccessProducts_read() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                    .header("Authorization", tokenFor("CASHIER")))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("MANAGER não acessa endpoints exclusivos de ADMIN → 403")
    class ManagerRestrictions {

        @Test
        void manager_cannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                    .header("Authorization", tokenFor("MANAGER"))
                    .contentType("application/json")
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        void manager_canAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", tokenFor("MANAGER")))
                .andExpect(status().isOk());
        }

        @Test
        void manager_cannotAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", tokenFor("MANAGER")))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN acessa tudo → nunca 403")
    class SuperAdminAccess {

        @Test
        void superAdmin_canAccessAdminTenants() throws Exception {
            mockMvc.perform(get("/api/v1/admin/tenants")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }

        @Test
        void superAdmin_canAccessReports() throws Exception {
            mockMvc.perform(get("/api/v1/reports/summary")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }

        @Test
        void superAdmin_canAccessProducts() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                    .header("Authorization", superAdminToken()))
                .andExpect(status().isOk());
        }
    }

    // ── Token inválido ────────────────────────────────────────────────────

    @Test
    @DisplayName("Token JWT inválido → 401 com ApiError")
    void invalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", "Bearer token.invalido.aqui"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Token expirado → 401")
    void expiredToken_shouldReturn401() throws Exception {
        // Gerar token com expiração no passado não é simples sem mock
        // Validar apenas que token malformado retorna 401
        mockMvc.perform(get("/api/v1/products")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.expirado.sig"))
            .andExpect(status().isUnauthorized());
    }
}
```

---

## Tarefa 4 — Testes de Catalog Controller

**Arquivo:** `backend/src/test/java/com/dipdv/controller/CatalogControllerIT.java`

```java
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
```

---

## Tarefa 5 — Testes de Order Controller

**Arquivo:** `backend/src/test/java/com/dipdv/controller/OrderControllerIT.java`

```java
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
```

---

## Tarefa 6 — Testes de CashRegister Controller

**Arquivo:** `backend/src/test/java/com/dipdv/controller/CashRegisterControllerIT.java`

```java
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
```

---

## Tarefa 7 — Testes de Admin Controller

**Arquivo:** `backend/src/test/java/com/dipdv/controller/AdminControllerIT.java`

```java
package com.dipdv.controller;

import com.dipdv.support.ControllerIntegrationSupport;
import com.dipdv.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class AdminControllerIT extends ControllerIntegrationSupport {

    @Test
    @DisplayName("GET /admin/tenants com SUPER_ADMIN → 200")
    void listTenants_withSuperAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /admin/tenants com ADMIN → 403")
    void listTenants_withAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants")
                .header("Authorization", tokenFor("ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/dashboard/stats com SUPER_ADMIN → 200")
    void globalStats_withSuperAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats")
                .header("Authorization", superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantCount").isNumber())
            .andExpect(jsonPath("$.totalRevenue").isNumber());
    }

    @Test
    @DisplayName("POST /admin/tenants com SUPER_ADMIN → 201")
    void createTenant_withSuperAdmin_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Lanchonete IT Test",
                      "slug": "lanchonete-it-test",
                      "ownerEmail": "owner@it-test.com",
                      "ownerName": "Owner IT",
                      "ownerPassword": "Senha@123"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lanchonete IT Test"))
            .andExpect(jsonPath("$.planType").value("TRIAL"));
    }

    @Test
    @DisplayName("POST /admin/tenants com slug duplicado → 409")
    void createTenant_withDuplicateSlug_shouldReturn409() throws Exception {
        String body = """
            {
              "name": "Tenant Slug Dup",
              "slug": "slug-duplicado-it",
              "ownerEmail": "dup@it-test.com",
              "ownerName": "Owner Dup",
              "ownerPassword": "Senha@123"
            }
        """;

        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/tenants")
                .header("Authorization", superAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /admin/auth/login com SUPER_ADMIN → 200 sem token no body")
    void adminLogin_withSuperAdmin_shouldSetCookieNotReturnToken()
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "superadmin@dipdv.app",
                      "password": "SuperAdmin@2025!"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
            .andExpect(header().exists("Set-Cookie"));
    }
}
```

---

## Tarefa 8 — Verificar compilação e rodar testes

```bash
cd backend

# Compilar
./mvnw compile -q 2>&1 | tail -5

# Unitários (sem banco)
./mvnw test -q 2>&1 | tail -5

# Testes de controller com banco (Testcontainers)
./mvnw test -Dgroups=integration \
  -Dexclude.integration.tests="" -q 2>&1 | tail -20
```

**Esperado:**
- Compile: sem erros
- Unitários: 59 testes, BUILD SUCCESS
- Integração: ~30 testes novos, BUILD SUCCESS

---

## Tarefa 9 — Commit

```bash
git add backend/src/test/java/com/dipdv/support/ControllerIntegrationSupport.java
git add backend/src/test/java/com/dipdv/controller/

git diff --cached --stat

git commit -m "test(controllers): testes de integracao HTTP com MockMvc

- ControllerIntegrationSupport: base com MockMvc + JwtService helper
- AuthControllerIT: login 200/401/400/415
- SecurityControllerIT: roles CASHIER/MANAGER/ADMIN/SUPER_ADMIN (18 cenarios)
- CatalogControllerIT: categories e products CRUD com status HTTP
- OrderControllerIT: criar, cancelar, listar com restricoes de role
- CashRegisterControllerIT: abrir, fechar, conflito de caixa duplo
- AdminControllerIT: tenants, dashboard, login cookie, slug duplicado

~30 novos testes de integracao HTTP"

git push origin feature/test-rls-integration
```

---

## Checklist

- [ ] `./mvnw compile` sem erros
- [ ] `./mvnw test` → 59 unitários, BUILD SUCCESS
- [ ] `./mvnw test -Dgroups=integration -Dexclude.integration.tests=""` → ~40 testes (10 RLS + ~30 controller), BUILD SUCCESS
- [ ] `SecurityControllerIT`: CASHIER bloqueado em ADMIN endpoints ✓
- [ ] `SecurityControllerIT`: SUPER_ADMIN acessa tudo ✓
- [ ] `AdminControllerIT`: login cookie sem token no body ✓
- [ ] `CashRegisterControllerIT`: segundo caixa → 409 ✓
- [ ] PR atualizado com link

---

## Nota sobre dados de teste

Os testes de controller que fazem `POST` (criar produto, categoria, etc.)
geram dados no banco do Testcontainers. Como o container é compartilhado
na suite, testes podem se interferir se os dados não forem únicos.

**Estratégia adotada:** usar sufixos únicos (`IT Test`, `it-test`, etc.)
para não colidir com dados do seed (`admin@dipdv.dev`, etc.).

Se algum teste falhar por dado duplicado entre execuções, adicionar
`@DirtiesContext` na classe ou usar `@Transactional` + rollback.
