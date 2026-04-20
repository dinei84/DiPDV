package com.dipdv.shared.security;

import java.util.UUID;

/**
 * UUID reservado para operações cross-tenant do SUPER_ADMIN.
 * O Kill Switch RLS exige este UUID + flag app.is_super_admin = 'true'.
 */
public final class MasterTenantConstants {

    public static final String MASTER_TENANT_ID_STR = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    public static final UUID   MASTER_TENANT_ID     = UUID.fromString(MASTER_TENANT_ID_STR);

    private MasterTenantConstants() {}
}
