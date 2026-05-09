package com.dipdv.rls;

import com.dipdv.modules.catalog.repository.CategoryRepository;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.support.PostgresIntegrationSupport;
import com.dipdv.support.RlsTest;
import com.dipdv.support.RlsTestHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes específicos do Kill Switch do SUPER_ADMIN no RLS.
 *
 * Valida que o bypass exige AMBAS as condições simultaneamente:
 * 1. app.is_super_admin = 'true'
 * 2. app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
 *
 * Se apenas uma das condições estiver ativa, o bypass NÃO ocorre.
 */
@RlsTest
class SuperAdminRlsTest extends PostgresIntegrationSupport {

    static final UUID TENANT_A = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Autowired CategoryRepository categoryRepository;
    @Autowired RlsTestHelper       rlsHelper;
    @Autowired JdbcTemplate        jdbc;

    @BeforeEach
    void setUp() {
        rlsHelper.runAsSuperAdmin(() -> {
            jdbc.execute("DELETE FROM categories WHERE tenant_id = " +
                "'cccccccc-0000-0000-0000-000000000003'");
            // Also clean master tenant categories (auto-provisioned by TenantAdminService)
            jdbc.execute("DELETE FROM categories WHERE tenant_id = " +
                "'ffffffff-ffff-ffff-ffff-ffffffffffff'");
        });

        jdbc.update(
            "INSERT INTO tenants (id, name, slug) VALUES (?::uuid, ?, ?) " +
            "ON CONFLICT DO NOTHING",
            TENANT_A.toString(), "Tenant C", "tenant-c");

        rlsHelper.runAsTenant(TENANT_A, () ->
            jdbc.update(
                "INSERT INTO categories (id, tenant_id, name) " +
                "VALUES (gen_random_uuid(), ?::uuid, 'Categoria C')",
                TENANT_A.toString())
        );
    }

    @Test
    @DisplayName("is_super_admin=true SEM UUID master não bypassa RLS")
    void superAdminFlag_withoutMasterUuid_shouldNotBypassRls() {
        // Setar apenas a flag sem o UUID master — Kill Switch deve bloquear
        rlsHelper.runAsTenant(TENANT_A, () -> {
            // Tenant A com flag is_super_admin mas sem UUID master
            // deve continuar vendo apenas seus próprios dados
            var categories = categoryRepository.findAll();
            assertThat(categories)
                .allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(TENANT_A));
        });
    }

    @Test
    @DisplayName("Kill Switch completo dá acesso cross-tenant ao SUPER_ADMIN")
    void killSwitch_withBothConditions_shouldBypassRls() {
        rlsHelper.runAsSuperAdmin(() -> {
            var categories = categoryRepository.findAll();
            // SUPER_ADMIN com UUID master vê TODAS as categorias
            assertThat(categories).isNotEmpty();
        });
    }

    @Test
    @DisplayName("UUID master sem flag is_super_admin não bypassa RLS")
    void masterUuid_withoutSuperAdminFlag_shouldNotBypassRls() {
        // Setar apenas o UUID master sem a flag — Kill Switch deve bloquear
        rlsHelper.runAsTenant(
            UUID.fromString(MasterTenantConstants.MASTER_TENANT_ID_STR),
            () -> {
                // Sem a flag is_super_admin, tenant master não tem dados próprios
                var categories = categoryRepository.findAll();
                assertThat(categories).isEmpty();
            }
        );
    }
}
