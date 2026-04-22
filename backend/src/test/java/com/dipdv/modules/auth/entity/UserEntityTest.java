package com.dipdv.modules.auth.entity;

import com.dipdv.shared.security.MasterTenantConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserEntityTest {

    @Test
    @DisplayName("guardMasterTenant deve permitir quando o usuário é SUPER_ADMIN")
    void guardMasterTenant_whenSuperAdmin_shouldAllow() {
        // Setup SecurityContext com SUPER_ADMIN
        var auth = new UsernamePasswordAuthenticationToken(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        User user = User.builder()
            .tenantId(MasterTenantConstants.MASTER_TENANT_ID)
            .build();

        try {
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(user, "guardMasterTenant"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("guardMasterTenant deve lançar SecurityException para tenant master se não for SUPER_ADMIN")
    void guardMasterTenant_whenRegularUser_shouldThrowSecurity() {
        // Setup SecurityContext com ADMIN normal
        var auth = new UsernamePasswordAuthenticationToken(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        User user = User.builder()
            .tenantId(MasterTenantConstants.MASTER_TENANT_ID)
            .build();

        try {
            assertThrows(SecurityException.class, () -> 
                ReflectionTestUtils.invokeMethod(user, "guardMasterTenant")
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("guardMasterTenant deve permitir qualquer usuário em tenant regular")
    void guardMasterTenant_whenRegularTenant_shouldAlwaysAllow() {
        var auth = new UsernamePasswordAuthenticationToken(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        User user = User.builder()
            .tenantId(UUID.randomUUID())
            .build();

        try {
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(user, "guardMasterTenant"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
