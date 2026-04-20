package com.dipdv.support;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Helper para ativar contexto de tenant nos testes de integração.
 *
 * Necessário porque os testes não passam pelo JwtAuthFilter/TenantFilter
 * do ciclo de vida HTTP. Precisamos ativar o RLS manualmente.
 *
 * Uso:
 *   rlsHelper.runAsTenant(TENANT_A_ID, () -> {
 *       List<Order> orders = orderRepository.findAll();
 *       assertThat(orders).allMatch(o -> o.getTenantId().equals(TENANT_A_ID));
 *   });
 */
@Component
public class RlsTestHelper {

    private final EntityManager entityManager;

    public RlsTestHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Executa uma ação no contexto de um tenant específico.
     * Ativa o RLS via SET LOCAL app.current_tenant.
     *
     * UUID.toString() produz apenas hex e hífens — concatenação segura.
     */
    @Transactional
    public void runAsTenant(UUID tenantId, Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantId + "'"
        ).executeUpdate();

        action.run();
    }

    /**
     * Executa uma ação no contexto do SUPER_ADMIN (bypass do RLS).
     * Requer AMBAS as condições: flag is_super_admin + UUID master.
     */
    @Transactional
    public void runAsSuperAdmin(Runnable action) {
        entityManager.createNativeQuery(
            "SET LOCAL app.is_super_admin = 'true'"
        ).executeUpdate();
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant = 'ffffffff-ffff-ffff-ffff-ffffffffffff'"
        ).executeUpdate();

        action.run();
    }

    /**
     * Insere dados diretamente no banco bypassando RLS.
     * Usado no setup de testes para criar dados de múltiplos tenants.
     */
    @Transactional
    public void insertAsSuperAdmin(Runnable insertAction) {
        runAsSuperAdmin(insertAction);
    }
}
