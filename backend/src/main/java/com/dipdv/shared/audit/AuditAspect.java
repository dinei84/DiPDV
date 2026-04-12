package com.dipdv.shared.audit;

import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Intercepta métodos anotados com @Auditable e grava em audit_log.
 *
 * @AfterReturning garante que só audita operações BEM-SUCEDIDAS.
 * Se o método lançar exceção, o log NÃO é gravado — correto por design.
 *
 * CONVENÇÃO: O primeiro parâmetro UUID do método anotado é o entityId.
 * Exemplo: cancelOrder(UUID orderId, String reason) → entityId = orderId
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(
            pointcut = "@annotation(auditable)",
            returning = "result"
    )
    public void logAudit(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            UUID tenantId = TenantContext.get();
            UUID userId = extractUserId();
            UUID entityId = extractEntityId(joinPoint);

            AuditLog auditLog = AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .action(auditable.action().name())
                    .entity(auditable.entity())
                    .entityId(entityId)
                    .payload(Map.of(
                            "method", joinPoint.getSignature().getName(),
                            "args", summarizeArgs(joinPoint.getArgs())
                    ))
                    .build();

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            // Falha na auditoria nunca deve derrubar a operação principal
            log.error("Falha ao gravar audit_log para {}: {}",
                    auditable.action(), e.getMessage());
        }
    }

    private UUID extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getDetails() instanceof DiPdvAuthDetails details) {
                return details.userId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private UUID extractEntityId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof UUID uuid) return uuid;
        }
        return null;
    }

    private String summarizeArgs(Object[] args) {
        // Nunca logar senhas ou dados sensíveis
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof UUID) {
                sb.append(arg).append(" ");
            } else if (arg instanceof String str && str.length() < 200) {
                sb.append(str).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
