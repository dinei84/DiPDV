package com.dipdv.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Marca testes que validam o isolamento de dados entre tenants via RLS.
 *
 * Estes são os testes mais críticos do sistema — provam que nenhuma
 * falha no código Java permite que tenant A veja dados do tenant B.
 * O RLS do PostgreSQL é a última linha de defesa.
 *
 * Todo teste @RlsTest deve:
 * 1. Criar dados em pelo menos 2 tenants distintos
 * 2. Ativar contexto de um tenant específico
 * 3. Verificar que apenas os dados daquele tenant são visíveis
 * 4. Verificar que dados dos outros tenants são invisíveis
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("rls")
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public @interface RlsTest {
}
