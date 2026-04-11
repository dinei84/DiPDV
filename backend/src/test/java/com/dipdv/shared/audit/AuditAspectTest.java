package com.dipdv.shared.audit;

import com.dipdv.shared.security.DiPdvAuthDetails;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testa o AuditAspect em isolamento, mockando JoinPoint e AuditLogRepository.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditAspect auditAspect;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENTITY_ID = UUID.randomUUID();

    private MockedStatic<TenantContext> mockedTenantContext;

    @BeforeEach
    void setUp() {
        mockedTenantContext = mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::get).thenReturn(TENANT_ID);

        DiPdvAuthDetails authDetails = new DiPdvAuthDetails(USER_ID, TENANT_ID, "MANAGER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of());
        ((UsernamePasswordAuthenticationToken) auth).setDetails(authDetails);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditAspect_whenMethodSucceeds_shouldSaveAuditLog() {
        // Arrange
        org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
        org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("cancelOrder");
        when(joinPoint.getArgs()).thenReturn(new Object[]{ENTITY_ID, "Motivo do cancelamento"});

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(AuditAction.ORDER_CANCELED);
        when(auditable.entity()).thenReturn("orders");

        // Act
        auditAspect.logAudit(joinPoint, auditable, null);

        // Assert
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void auditAspect_whenTenantContextEmpty_shouldNotBreakOperation() {
        // Arrange — TenantContext.get() retorna null (contexto sem tenant)
        mockedTenantContext.when(TenantContext::get).thenReturn(null);

        org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
        org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("cancelOrder");
        when(joinPoint.getArgs()).thenReturn(new Object[]{ENTITY_ID});

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(AuditAction.ORDER_CANCELED);
        when(auditable.entity()).thenReturn("orders");

        // Act — não deve lançar exceção
        auditAspect.logAudit(joinPoint, auditable, null);

        // Save ainda é chamado (tenantId pode ser null no AuditLog)
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void auditAspect_whenRepositoryThrows_shouldNotPropagateException() {
        // Arrange — repositório lança exceção (ex: banco indisponível)
        doThrow(new RuntimeException("DB connection error"))
                .when(auditLogRepository).save(any());

        org.aspectj.lang.JoinPoint joinPoint = mock(org.aspectj.lang.JoinPoint.class);
        org.aspectj.lang.reflect.MethodSignature signature = mock(org.aspectj.lang.reflect.MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("cancelOrder");
        when(joinPoint.getArgs()).thenReturn(new Object[]{ENTITY_ID});

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(AuditAction.ORDER_CANCELED);
        when(auditable.entity()).thenReturn("orders");

        // Act — falha no audit NÃO deve propagar para a operação principal
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> auditAspect.logAudit(joinPoint, auditable, null));
    }
}
