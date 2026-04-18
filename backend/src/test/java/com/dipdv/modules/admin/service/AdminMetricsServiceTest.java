package com.dipdv.modules.admin.service;

import com.dipdv.modules.admin.dto.GlobalStatsResponse;
import com.dipdv.modules.admin.dto.TenantMetricsResponse;
import com.dipdv.modules.admin.repository.AdminRepository;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private TenantContextService tenantContextService;

    @InjectMocks
    private AdminMetricsService adminMetricsService;

    @Test
    void getGlobalStats_shouldActivateSuperAdminContext() {
        GlobalStatsResponse stub = new GlobalStatsResponse(2L, 1L, 10L, 500.0, List.of());
        when(adminRepository.getGlobalStats()).thenReturn(stub);

        GlobalStatsResponse result = adminMetricsService.getGlobalStats();

        verify(tenantContextService).applyTenantContextSuperAdmin(
                eq(MasterTenantConstants.MASTER_TENANT_ID));
        assertNotNull(result);
        assertEquals(2L, result.tenantCount());
        assertEquals(500.0, result.totalRevenue());
    }

    @Test
    void getEngagementMetrics_shouldReturnStatusForEachTenant() {
        List<TenantMetricsResponse> stub = List.of(
                new TenantMetricsResponse(UUID.randomUUID(), "Tenant A", "TRIAL",
                        null, 5L, 100.0, "ACTIVE"),
                new TenantMetricsResponse(UUID.randomUUID(), "Tenant B", "BASIC",
                        null, 0L, 0.0, "NEVER")
        );
        when(adminRepository.getEngagementMetrics()).thenReturn(stub);

        List<TenantMetricsResponse> result = adminMetricsService.getEngagementMetrics();

        verify(tenantContextService).applyTenantContextSuperAdmin(
                eq(MasterTenantConstants.MASTER_TENANT_ID));
        assertEquals(2, result.size());
        assertEquals("ACTIVE", result.get(0).engagementStatus());
        assertEquals("NEVER", result.get(1).engagementStatus());
    }
}
