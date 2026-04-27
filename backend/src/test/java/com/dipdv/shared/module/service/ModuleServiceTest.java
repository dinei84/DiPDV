package com.dipdv.shared.module.service;

import com.dipdv.shared.module.entity.Module;
import com.dipdv.shared.module.repository.ModuleRepository;
import com.dipdv.shared.module.repository.TenantModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private TenantModuleRepository tenantModuleRepository;

    @InjectMocks
    private ModuleService moduleService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("isEnabled deve retornar true para módulos BASE")
    void shouldReturnTrueForBaseModules() {
        String moduleCode = "PDV_BASIC";
        Module baseModule = Module.builder().code(moduleCode).tier("BASE").build();
        
        when(moduleRepository.findById(moduleCode)).thenReturn(Optional.of(baseModule));

        assertTrue(moduleService.isEnabled(tenantId, moduleCode));
        verify(moduleRepository).findById(moduleCode);
        verifyNoInteractions(tenantModuleRepository);
    }

    @Test
    @DisplayName("isEnabled deve retornar true para módulos PAID se habilitados para o tenant")
    void shouldReturnTrueForEnabledPaidModules() {
        String moduleCode = "REPORTS";
        Module paidModule = Module.builder().code(moduleCode).tier("PAID").build();
        
        when(moduleRepository.findById(moduleCode)).thenReturn(Optional.of(paidModule));
        when(tenantModuleRepository.isModuleEnabledForTenant(tenantId, moduleCode)).thenReturn(true);

        assertTrue(moduleService.isEnabled(tenantId, moduleCode));
    }

    @Test
    @DisplayName("isEnabled deve retornar false para módulos PAID se desabilitados para o tenant")
    void shouldReturnFalseForDisabledPaidModules() {
        String moduleCode = "REPORTS";
        Module paidModule = Module.builder().code(moduleCode).tier("PAID").build();
        
        when(moduleRepository.findById(moduleCode)).thenReturn(Optional.of(paidModule));
        when(tenantModuleRepository.isModuleEnabledForTenant(tenantId, moduleCode)).thenReturn(false);

        assertFalse(moduleService.isEnabled(tenantId, moduleCode));
    }

    @Test
    @DisplayName("isEnabled deve retornar true para SUPER_ADMIN mesmo se módulo for PAID e inativo")
    void shouldReturnTrueForSuperAdmin() {
        String moduleCode = "REPORTS";
        setupSuperAdminAuth();

        assertTrue(moduleService.isEnabled(tenantId, moduleCode));
        verifyNoInteractions(moduleRepository, tenantModuleRepository);
    }

    @Test
    @DisplayName("disableModule deve lançar exceção ao tentar desabilitar módulo BASE")
    void shouldThrowExceptionWhenDisablingBaseModule() {
        String moduleCode = "PDV_BASIC";
        Module baseModule = Module.builder().code(moduleCode).tier("BASE").build();
        
        when(moduleRepository.findById(moduleCode)).thenReturn(Optional.of(baseModule));

        assertThrows(IllegalStateException.class, () -> 
            moduleService.disableModule(tenantId, moduleCode, UUID.randomUUID()));
    }

    private void setupSuperAdminAuth() {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        
        when(securityContext.getAuthentication()).thenReturn(auth);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))).when(auth).getAuthorities();
        SecurityContextHolder.setContext(securityContext);
    }
}
