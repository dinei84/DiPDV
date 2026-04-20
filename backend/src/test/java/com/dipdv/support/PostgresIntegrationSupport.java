package com.dipdv.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Suporte base para testes com PostgreSQL real via Testcontainers.
 *
 * Padrão Singleton Container: o container é iniciado UMA VEZ via static
 * initializer e vive até o fim da JVM (Ryuk cuida da limpeza).
 * Isso evita que o container pare entre classes de teste e garante que
 * o @DynamicPropertySource capture sempre a mesma porta.
 *
 * Separação de usuários:
 * - Flyway roda como "postgres" (superusuário) para criar schemas/policies.
 * - JPA/JdbcTemplate usam "dipdv_app" (não-superusuário) → RLS é aplicado.
 * O init script (sql/init-dipdv-app.sql) cria dipdv_app ANTES do Flyway.
 */
public abstract class PostgresIntegrationSupport {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("dipdv_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("sql/init-dipdv-app.sql");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Datasource da aplicação: dipdv_app (não-superusuário) → RLS ativo
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "dipdv_app");
        registry.add("spring.datasource.password", () -> "dipdv_test");

        // Flyway usa o usuário admin para poder criar roles e habilitar RLS
        registry.add("spring.flyway.url",      postgres::getJdbcUrl);
        registry.add("spring.flyway.user",     postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",  () -> "true");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
