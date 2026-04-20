package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Marca testes de integração que sobem o contexto Spring completo
 * com banco PostgreSQL real via Testcontainers.
 *
 * Uso: @IntegrationTest na classe de teste.
 * Herança: a classe deve estender AbstractModuleIntegrationTest
 * ou um dos módulos específicos.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
