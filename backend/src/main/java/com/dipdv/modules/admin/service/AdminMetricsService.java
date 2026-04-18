package com.dipdv.modules.admin.service;

import com.dipdv.modules.admin.dto.GlobalStatsResponse;
import com.dipdv.modules.admin.dto.TenantMetricsResponse;
import com.dipdv.modules.admin.repository.AdminRepository;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final AdminRepository adminRepository;
    private final TenantContextService tenantContextService;

    @Transactional(readOnly = true)
    public GlobalStatsResponse getGlobalStats() {
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getGlobalStats();
    }

    @Transactional(readOnly = true)
    public List<TenantMetricsResponse> getEngagementMetrics() {
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getEngagementMetrics();
    }
}
