package com.dipdv.modules.admin.service;

import com.dipdv.modules.admin.dto.CreateTenantRequest;
import com.dipdv.modules.admin.dto.TenantListResponse;
import com.dipdv.modules.admin.dto.UpdateTenantStatusRequest;
import com.dipdv.modules.admin.repository.AdminRepository;
import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.repository.UserRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContextService;
import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.enums.TenantPlan;
import com.dipdv.shared.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTenantServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantContextService tenantContextService;

    @InjectMocks
    private AdminTenantService adminTenantService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createTenant_whenSlugAlreadyExists_shouldThrowConflict() {
        CreateTenantRequest request = new CreateTenantRequest(
                "Lanchonete Teste", "slug-existente",
                "dono@teste.com", "João", "Senha@123");

        when(tenantRepository.existsBySlug("slug-existente")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminTenantService.createTenant(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void createTenant_whenValid_shouldCreateTenantAndOwner() {
        CreateTenantRequest request = new CreateTenantRequest(
                "Nova Lanchonete", "nova-lanchonete",
                "dono@nova.com", "Maria", "Senha@456");

        when(tenantRepository.existsBySlug("nova-lanchonete")).thenReturn(false);
        when(passwordEncoder.encode("Senha@456")).thenReturn("hash-encoded");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        when(tenantRepository.save(tenantCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        when(adminRepository.listAllTenants()).thenAnswer(inv -> {
            UUID savedId = tenantCaptor.getValue().getId();
            return List.of(new TenantListResponse(
                    savedId, "Nova Lanchonete", "nova-lanchonete",
                    "dono@nova.com", "TRIAL", true, null, OffsetDateTime.now(), 1L));
        });

        TenantListResponse result = adminTenantService.createTenant(request);

        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
        assertEquals("TRIAL", result.planType());
    }

    @Test
    void updateTenantStatus_whenSuspended_shouldSetPlanSuspended() {
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest(
                TenantPlan.SUSPENDED, false, "Inadimplência");

        Tenant tenant = Tenant.builder().id(TENANT_ID).name("Tenant").active(true)
                .planType(TenantPlan.TRIAL).build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        TenantListResponse stubResponse = new TenantListResponse(
                TENANT_ID, "Tenant", "tenant", null, "SUSPENDED", false,
                null, OffsetDateTime.now(), 0L);
        when(adminRepository.listAllTenants()).thenReturn(List.of(stubResponse));

        TenantListResponse result = adminTenantService.updateTenantStatus(TENANT_ID, request);

        verify(tenantRepository).save(tenant);
        assertEquals("SUSPENDED", result.planType());
    }

    @Test
    void updateTenantStatus_whenDeactivatingWithoutReason_shouldThrowBadRequest() {
        UpdateTenantStatusRequest request = new UpdateTenantStatusRequest(
                TenantPlan.TRIAL, false, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminTenantService.updateTenantStatus(TENANT_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void deactivateTenant_whenMasterTenant_shouldThrowForbidden() {
        UUID masterId = MasterTenantConstants.MASTER_TENANT_ID;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminTenantService.deactivateTenant(masterId, "qualquer motivo"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void getTenantSummary_whenMasterTenant_shouldThrowForbidden() {
        UUID masterId = MasterTenantConstants.MASTER_TENANT_ID;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminTenantService.getTenantSummary(masterId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(adminRepository, never()).getTenantSummary(any());
    }
}
