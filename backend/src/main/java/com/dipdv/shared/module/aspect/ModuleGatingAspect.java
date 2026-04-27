package com.dipdv.shared.module.aspect;

import com.dipdv.shared.module.annotation.RequiresModule;
import com.dipdv.shared.module.exception.ModuleNotEnabledException;
import com.dipdv.shared.module.service.ModuleService;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class ModuleGatingAspect {

    private final ModuleService moduleService;

    @Pointcut("@within(com.dipdv.shared.module.annotation.RequiresModule)")
    public void classAnnotated() {}

    @Pointcut("@annotation(com.dipdv.shared.module.annotation.RequiresModule)")
    public void methodAnnotated() {}

    @Before("classAnnotated() && @within(requiresModule)")
    public void checkModuleClass(RequiresModule requiresModule) {
        doCheck(requiresModule);
    }

    @Before("methodAnnotated() && @annotation(requiresModule)")
    public void checkModuleMethod(RequiresModule requiresModule) {
        doCheck(requiresModule);
    }

    private void doCheck(RequiresModule requiresModule) {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }

        String moduleCode = requiresModule.value();
        if (!moduleService.isEnabled(tenantId, moduleCode)) {
            throw new ModuleNotEnabledException(moduleCode);
        }
    }
}
