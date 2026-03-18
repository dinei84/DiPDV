package com.dipdv.shared.tenant;

import java.util.UUID;

/**
 * Armazena o tenant_id do request atual em um ThreadLocal.
 *
 * ThreadLocal garante que cada thread (cada request HTTP no servidor)
 * tenha seu próprio valor isolado — sem risco de um request "vazar"
 * o tenant para outro request em paralelo.
 *
 * Ciclo de vida:
 *   1. TenantFilter.doFilter() → chama TenantContext.set()
 *   2. TenantFilter.doFilter() → chama TenantContext.clear() no finally{}
 *
 * IMPORTANTE: clear() é obrigatório no finally{} do Filter.
 * Servidores como Tomcat reutilizam threads (thread pool).
 * Sem clear(), o próximo request na mesma thread herdaria o tenant anterior.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    private TenantContext() {
        // Utilitário estático — não instanciar
    }

    public static void set(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID get() {
        return currentTenant.get();
    }

    /**
     * Retorna o tenant atual ou lança exceção se não estiver definido.
     * Use em Services onde o tenant é sempre obrigatório.
     */
    public static UUID getRequired() {
        UUID tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                "TenantContext não inicializado. " +
                "Verifique se o TenantFilter está registrado na cadeia de filtros."
            );
        }
        return tenantId;
    }

    /**
     * SEMPRE chamar no finally{} do TenantFilter.
     * Evita vazamento de tenant entre requests no pool de threads.
     */
    public static void clear() {
        currentTenant.remove();
    }
}
