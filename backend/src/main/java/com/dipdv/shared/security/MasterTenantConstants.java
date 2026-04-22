package com.dipdv.shared.security;

import java.util.UUID;

/**
 * UUID reservado para o contexto do SUPER_ADMIN.
 * Nenhum tenant de cliente pode ter este UUID.
 * Verificado via @PrePersist em todas as entidades com tenant_id.
 */
public final class MasterTenantConstants {

    public static final UUID MASTER_TENANT_ID =
        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    public static final String MASTER_TENANT_ID_STR =
        "ffffffff-ffff-ffff-ffff-ffffffffffff";

    private MasterTenantConstants() {}

    public static boolean isMasterTenant(UUID tenantId) {
        return MASTER_TENANT_ID.equals(tenantId);
    }
}
