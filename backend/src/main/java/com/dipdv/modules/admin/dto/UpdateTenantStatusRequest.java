package com.dipdv.modules.admin.dto;

import com.dipdv.shared.tenant.enums.TenantPlan;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(
        @NotNull TenantPlan planType,
        Boolean active,
        String reason
) {}
