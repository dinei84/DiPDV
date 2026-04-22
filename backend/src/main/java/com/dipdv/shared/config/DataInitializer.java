package com.dipdv.shared.config;

import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cria dados iniciais de teste APENAS no perfil dev.
 * NUNCA executar em produção — anotação @Profile("dev") garante isso.
 *
 * Credenciais de teste:
 *   tenantId: 00000000-0000-0000-0000-000000000001 (fixo para facilitar testes)
 *   email:    admin@dipdv.dev
 *   senha:    dipdv@2025
 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    // UUID fixo para facilitar os testes — não alterar
    private static final UUID DEV_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    @Transactional
    public void run(String... args) {
        // Necessário setar app.current_tenant para o Hibernate conseguir ler/inserir com RLS ativo
        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + DEV_TENANT_ID + "'");

        if (userRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(
                "admin@dipdv.dev", DEV_TENANT_ID)) {
            log.info("[DEV] Usuário de teste já existe — pulando seed");
            return;
        }

        // 1. Inserir tenant de teste (ON CONFLICT garante idempotência)
        // Bypassa RLS pois é inicialização via JdbcTemplate direto
        jdbcTemplate.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) ON CONFLICT DO NOTHING",
            DEV_TENANT_ID.toString(),
            "Lanchonete Dev",
            "dev-tenant"
        );

        // 2. Inserir usuário admin vinculado ao tenant
        User admin = User.builder()
            .tenantId(DEV_TENANT_ID)
            .email("admin@dipdv.dev")
            .passwordHash(passwordEncoder.encode("dipdv@2025"))
            .name("Admin Dev")
            .role(UserRole.ADMIN)
            .build();

        userRepository.save(admin);

        log.info("╔══════════════════════════════════════════╗");
        log.info("║         SEED DE DESENVOLVIMENTO          ║");
        log.info("╠══════════════════════════════════════════╣");
        log.info("║  tenantId : {}  ║", DEV_TENANT_ID);
        log.info("║  email    : admin@dipdv.dev              ║");
        log.info("║  senha    : dipdv@2025                   ║");
        log.info("║  role     : ADMIN                        ║");
        log.info("╚══════════════════════════════════════════╝");
    }
}
