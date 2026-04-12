package com.dipdv.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private TenantContextService tenantContextService;

    @Test
    void applyTenantContext_whenValidUuid_shouldExecuteSetLocal() {
        UUID tenantId = UUID.randomUUID();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        tenantContextService.applyTenantContext(tenantId);

        // Verificar que o SQL executado contém o UUID correto
        verify(entityManager).createNativeQuery(
            "SET LOCAL app.current_tenant = '" + tenantId.toString().toLowerCase() + "'"
        );
        verify(query).executeUpdate();
    }

    @Test
    void applyTenantContext_whenNullUuid_shouldThrowException() {
        // UUID null não pode chegar aqui — o tipo Java garante,
        // mas documentamos o comportamento esperado
        assertThrows(NullPointerException.class, () ->
            tenantContextService.applyTenantContext(null)
        );
    }
}
