package com.dipdv.modules.admin.service;

import com.dipdv.modules.admin.dto.CreateTenantRequest;
import com.dipdv.modules.admin.dto.TenantListResponse;
import com.dipdv.modules.admin.dto.TenantSummaryResponse;
import com.dipdv.modules.admin.dto.UpdateTenantStatusRequest;
import com.dipdv.modules.admin.repository.AdminRepository;
import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.modules.auth.repository.UserRepository;
import com.dipdv.shared.audit.AuditAction;
import com.dipdv.shared.audit.Auditable;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContextService;
import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.enums.TenantPlan;
import com.dipdv.shared.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTenantService {

    private final AdminRepository adminRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextService tenantContextService;

    @Transactional(readOnly = true)
    public List<TenantListResponse> listTenants() {
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.listAllTenants();
    }

    @Transactional(readOnly = true)
    public TenantSummaryResponse getTenantSummary(UUID tenantId) {
        guardNotMasterTenant(tenantId);
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);
        return adminRepository.getTenantSummary(tenantId);
    }

    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_CREATED, entity = "tenants")
    public TenantListResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new BusinessException("Slug já está em uso: " + request.slug(), HttpStatus.CONFLICT);
        }

        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .slug(request.slug())
                .ownerEmail(request.ownerEmail())
                .active(true)
                .planType(TenantPlan.TRIAL)
                .trialUntil(OffsetDateTime.now().plusDays(30))
                .build();

        tenantRepository.save(tenant);

        User owner = User.builder()
                .tenantId(tenant.getId())
                .email(request.ownerEmail())
                .passwordHash(passwordEncoder.encode(request.ownerPassword()))
                .name(request.ownerName())
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        userRepository.save(owner);

        log.info("[SUPER_ADMIN] Tenant criado: id={} slug={} owner={}",
                tenant.getId(), tenant.getSlug(), request.ownerEmail());

        return adminRepository.listAllTenants().stream()
                .filter(t -> t.id().equals(tenant.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_UPDATED, entity = "tenants")
    public TenantListResponse updateTenantStatus(UUID tenantId, UpdateTenantStatusRequest request) {
        guardNotMasterTenant(tenantId);

        if (Boolean.FALSE.equals(request.active())
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException("Motivo obrigatório ao desativar um tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.NOT_FOUND));

        tenant.setPlanType(request.planType());
        if (request.active() != null) {
            tenant.setActive(request.active());
        }

        tenantRepository.save(tenant);

        log.info("[SUPER_ADMIN] Tenant atualizado: id={} plan={} active={}",
                tenantId, request.planType(), request.active());

        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);

        return adminRepository.listAllTenants().stream()
                .filter(t -> t.id().equals(tenantId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    @Auditable(action = AuditAction.SUPER_ADMIN_TENANT_DEACTIVATED, entity = "tenants")
    public void deactivateTenant(UUID tenantId, String reason) {
        guardNotMasterTenant(tenantId);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Motivo obrigatório para desativar tenant", HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.NOT_FOUND));

        tenant.setActive(false);
        tenant.setPlanType(TenantPlan.SUSPENDED);
        tenantRepository.save(tenant);

        log.warn("[SUPER_ADMIN] Tenant SUSPENSO: id={} reason={}", tenantId, reason);
    }

    private void guardNotMasterTenant(UUID tenantId) {
        if (MasterTenantConstants.isMasterTenant(tenantId)) {
            throw new BusinessException("Operação não permitida no tenant master", HttpStatus.FORBIDDEN);
        }
    }
}
