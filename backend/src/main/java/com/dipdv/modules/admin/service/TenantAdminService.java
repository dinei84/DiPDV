package com.dipdv.modules.admin.service;

import com.dipdv.modules.admin.dto.TenantRequest;
import com.dipdv.modules.admin.dto.TenantResponse;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.module.service.ModuleService;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContextService;
import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private static final List<String> BASE_MODULE_CODES = List.of(
            "PDV_BASIC",
            "CATALOG_MANAGEMENT"
    );

    private final TenantRepository tenantRepository;
    private final TenantContextService tenantContextService;
    private final ModuleService moduleService;

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        applySuperAdminContext();
        return tenantRepository.findAllByIdNotOrderByCreatedAtDesc(MasterTenantConstants.MASTER_TENANT_ID)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        applySuperAdminContext();
        return toResponse(findTenant(tenantId));
    }

    @Transactional
    public TenantResponse createTenant(TenantRequest request, UUID actorUserId) {
        applySuperAdminContext();

        String name = normalizeName(request.name());
        String slug = normalizeSlug(request.slug(), name);

        if (tenantRepository.existsBySlug(slug)) {
            throw new BusinessException("Slug já está em uso: " + slug, HttpStatus.CONFLICT);
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .active(true)
                .build());

        BASE_MODULE_CODES.forEach(moduleCode ->
                moduleService.enableModule(tenant.getId(), moduleCode, actorUserId));

        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, TenantRequest request) {
        applySuperAdminContext();

        Tenant tenant = findTenant(tenantId);

        if (request.name() != null) {
            tenant.setName(normalizeName(request.name()));
        }

        if (request.slug() != null) {
            String newSlug = normalizeSlug(request.slug(), tenant.getName());
            if (!newSlug.equals(tenant.getSlug()) && tenantRepository.existsBySlug(newSlug)) {
                throw new BusinessException("Slug já está em uso: " + newSlug, HttpStatus.CONFLICT);
            }
            tenant.setSlug(newSlug);
        }

        if (request.active() != null) {
            tenant.setActive(request.active());
        }

        tenantRepository.save(tenant);
        return toResponse(tenant);
    }

    private Tenant findTenant(UUID tenantId) {
        guardNotMasterTenant(tenantId);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.NOT_FOUND));
    }

    private TenantResponse toResponse(Tenant tenant) {
        Instant createdAt = tenant.getCreatedAt() != null
                ? tenant.getCreatedAt().toInstant()
                : OffsetDateTime.now().toInstant();

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.isActive(),
                createdAt,
                moduleService.listEnabledModules(tenant.getId())
        );
    }

    private void applySuperAdminContext() {
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);
    }

    private void guardNotMasterTenant(UUID tenantId) {
        if (MasterTenantConstants.isMasterTenant(tenantId)) {
            throw new BusinessException("Operação não permitida no tenant master", HttpStatus.FORBIDDEN);
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Nome do tenant é obrigatório", HttpStatus.BAD_REQUEST);
        }
        return name.trim();
    }

    private String normalizeSlug(String slug, String fallbackName) {
        String candidate = (slug == null || slug.isBlank()) ? fallbackName : slug;
        String normalized = Normalizer.normalize(candidate, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            throw new BusinessException("Slug inválido para o tenant", HttpStatus.BAD_REQUEST);
        }

        return normalized;
    }
}
