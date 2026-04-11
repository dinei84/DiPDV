package com.dipdv.modules.nfce.service;

import com.dipdv.modules.nfce.entity.NfceDocument;
import com.dipdv.modules.nfce.entity.enums.NfceStatus;
import com.dipdv.modules.nfce.repository.NfceDocumentRepository;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockNfceServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private NfceDocumentRepository nfceDocumentRepository;

    private MockedStatic<TenantContext> mockedTenantContext;

    private MockNfceService mockNfceService;

    @BeforeEach
    void setUp() {
        mockNfceService = new MockNfceService(nfceDocumentRepository);
        mockedTenantContext = org.mockito.Mockito.mockStatic(TenantContext.class);
        mockedTenantContext.when(TenantContext::getRequired).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
    }

    @Test
    void emit_whenCreatingMockDocument_shouldGenerate44DigitAccessKey() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(nfceDocumentRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
                .thenReturn(Optional.empty());
        when(nfceDocumentRepository.save(any(NfceDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NfceDocument document = mockNfceService.emit(orderId, paymentId);

        assertEquals(NfceStatus.ISSUED, document.getStatus());
        assertNotNull(document.getAccessKey());
        assertEquals(44, document.getAccessKey().length());
        assertTrue(document.getAccessKey().chars().allMatch(Character::isDigit));
    }
}
