package com.dipdv.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

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
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "dipdv_app");
        registry.add("spring.datasource.password", () -> "dipdv_test");
        registry.add("spring.flyway.url",          postgres::getJdbcUrl);
        registry.add("spring.flyway.user",         () -> "postgres");
        registry.add("spring.flyway.password",     () -> "postgres");
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}