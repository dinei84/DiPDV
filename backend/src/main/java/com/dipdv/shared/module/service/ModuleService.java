package com.dipdv.shared.module.service;

import com.dipdv.shared.module.entity.Module;
import com.dipdv.shared.module.entity.TenantModule;
import com.dipdv.shared.module.entity.TenantModuleId;
import com.dipdv.shared.module.repository.ModuleRepository;
import com.dipdv.shared.module.repository.TenantModuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final TenantModuleRepository tenantModuleRepository;

    /**
     * Verifica se um módulo está habilitado para o tenant.
     * SUPER_ADMIN sempre tem acesso a tudo.
     * Módulos BASE sempre estão habilitados.
     */
    public boolean isEnabled(UUID tenantId, String moduleCode) {
        if (isSuperAdmin()) {
            return true;
        }

        Module module = moduleRepository.findById(moduleCode)
                .orElse(null);
        
        if (module == null) {
            log.warn("Tentativa de verificar módulo inexistente: {}", moduleCode);
            return false;
        }

        if ("BASE".equals(module.getTier())) {
            return true;
        }

        return tenantModuleRepository.isModuleEnabledForTenant(tenantId, moduleCode);
    }

    public List<String> listEnabledModules(UUID tenantId) {
        List<String> enabledPaidModules = tenantModuleRepository.findEnabledModuleCodesByTenantId(tenantId);
        List<String> baseModules = moduleRepository.findAll().stream()
                .filter(m -> "BASE".equals(m.getTier()))
                .map(Module::getCode)
                .collect(Collectors.toList());
        
        baseModules.addAll(enabledPaidModules);
        return baseModules.stream().distinct().collect(Collectors.toList());
    }

    @Transactional
    public void enableModule(UUID tenantId, String moduleCode, UUID actorUserId) {
        Module module = moduleRepository.findById(moduleCode)
                .orElseThrow(() -> new IllegalArgumentException("Módulo não encontrado: " + moduleCode));

        TenantModuleId id = new TenantModuleId(tenantId, moduleCode);
        TenantModule tenantModule = tenantModuleRepository.findById(id)
                .map(tm -> {
                    tm.setEnabled(true);
                    return tm;
                })
                .orElseGet(() -> TenantModule.builder()
                        .id(id)
                        .enabled(true)
                        .enabledBy(actorUserId)
                        .build());

        tenantModuleRepository.save(tenantModule);
        log.info("Módulo {} habilitado para o tenant {}", moduleCode, tenantId);
    }

    @Transactional
    public void disableModule(UUID tenantId, String moduleCode, UUID actorUserId) {
        Module module = moduleRepository.findById(moduleCode)
                .orElseThrow(() -> new IllegalArgumentException("Módulo não encontrado: " + moduleCode));

        if ("BASE".equals(module.getTier())) {
            throw new IllegalStateException("Não é possível desabilitar um módulo de nível BASE");
        }

        TenantModuleId id = new TenantModuleId(tenantId, moduleCode);
        tenantModuleRepository.findById(id).ifPresent(tm -> {
            tm.setEnabled(false);
            tenantModuleRepository.save(tm);
            log.info("Módulo {} desabilitado para o tenant {}", moduleCode, tenantId);
        });
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
