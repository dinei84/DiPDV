package com.dipdv.modules.admin.dto;

public record TenantRequest(
        String name,
        String slug,
        Boolean active
) {}
