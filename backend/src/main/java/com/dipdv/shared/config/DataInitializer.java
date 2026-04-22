package com.dipdv.shared.config;

import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.shared.security.MasterTenantConstants;
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
 * NUNCA executar em produção — anotação @Profile({"dev", "test"}) garante isso.
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

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    // UUID fixo para facilitar os testes — não alterar
    private static final UUID DEV_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID MASTER_TENANT_ID = MasterTenantConstants.MASTER_TENANT_ID;
    private static final String SUPER_ADMIN_EMAIL = "superadmin@dipdv.app";

    @Override
    @Transactional
    public void run(String... args) {
        // SET deve coincidir com o tenant_id de cada bloco para passar o RLS WITH CHECK
        jdbcTemplate.execute("SET app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'");
        Integer saCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ? " +
            "AND tenant_id = ?::uuid AND deleted_at IS NULL",
            Integer.class, SUPER_ADMIN_EMAIL, MASTER_TENANT_ID.toString());
        if (saCount == null || saCount == 0) {

            jdbcTemplate.update(
                "INSERT INTO users (id, tenant_id, email, password_hash, name, role, active) " +
                "VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid, ?::uuid, ?, ?, ?, ?::user_role, true) ON CONFLICT (id) DO NOTHING",
                MASTER_TENANT_ID.toString(),
                SUPER_ADMIN_EMAIL,
                passwordEncoder.encode("SuperAdmin@2025!"),
                "Super Admin DiPDV",
                UserRole.SUPER_ADMIN.name()
            );

            log.info("╔══════════════════════════════════════════╗");
            log.info("║         SUPER ADMIN CRIADO               ║");
            log.info("╠══════════════════════════════════════════╣");
            log.info("║  email  : superadmin@dipdv.app           ║");
            log.info("║  senha  : SuperAdmin@2025!               ║");
            log.info("║  role   : SUPER_ADMIN                    ║");
            log.info("╚══════════════════════════════════════════╝");
        }

        jdbcTemplate.execute("SET app.current_tenant = '00000000-0000-0000-0000-000000000001'");
        Integer adminCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ? " +
            "AND tenant_id = ?::uuid AND deleted_at IS NULL",
            Integer.class, "admin@dipdv.dev", DEV_TENANT_ID.toString());
        if (adminCount != null && adminCount > 0) {
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
        jdbcTemplate.update(
            "INSERT INTO users (id, tenant_id, email, password_hash, name, role, active) " +
            "VALUES ('00000000-0000-0000-0001-000000000001'::uuid, ?::uuid, ?, ?, ?, ?::user_role, true) ON CONFLICT (id) DO NOTHING",
            DEV_TENANT_ID.toString(),
            "admin@dipdv.dev",
            passwordEncoder.encode("dipdv@2025"),
            "Admin Dev",
            UserRole.ADMIN.name()
        );

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
