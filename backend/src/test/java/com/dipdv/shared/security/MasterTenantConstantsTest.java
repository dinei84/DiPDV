package com.dipdv.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MasterTenantConstantsTest {

    @Test
    @DisplayName("isMasterTenant deve retornar true quando UUID for o master")
    void isMasterTenant_whenMasterUuid_shouldReturnTrue() {
        assertTrue(MasterTenantConstants.isMasterTenant(MasterTenantConstants.MASTER_TENANT_ID));
    }

    @Test
    @DisplayName("isMasterTenant deve retornar false quando UUID for regular")
    void isMasterTenant_whenRegularUuid_shouldReturnFalse() {
        assertFalse(MasterTenantConstants.isMasterTenant(UUID.randomUUID()));
    }
}
