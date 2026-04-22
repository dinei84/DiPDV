package com.dipdv.support;

import com.dipdv.shared.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    () -> "dipdv_app");
        registry.add("spring.datasource.password",    () -> "dipdv_test");
        registry.add("spring.flyway.url",             postgres::getJdbcUrl);
        registry.add("spring.flyway.user",            () -> "postgres");
        registry.add("spring.flyway.password",        () -> "postgres");
        registry.add("spring.flyway.enabled",         () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    protected static final UUID TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    protected static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0001-000000000001");

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
