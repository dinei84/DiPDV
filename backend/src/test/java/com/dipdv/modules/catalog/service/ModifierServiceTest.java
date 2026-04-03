package com.dipdv.modules.catalog.service;

import com.dipdv.modules.catalog.dto.modifier.ModifierGroupRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierGroupResponse;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionResponse;
import com.dipdv.modules.catalog.entity.ModifierGroup;
import com.dipdv.modules.catalog.entity.ModifierOption;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.entity.embedded.ProductModifierGroupId;
import com.dipdv.modules.catalog.repository.ModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ModifierOptionRepository;
import com.dipdv.modules.catalog.repository.ProductModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModifierServiceTest {

    @Mock
    private ModifierGroupRepository groupRepository;

    @Mock
    private ModifierOptionRepository optionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductModifierGroupRepository productModifierGroupRepository;

    @InjectMocks
    private ModifierService modifierService;

    private MockedStatic<TenantContext> tenantContextMock;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantContextMock = Mockito.mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getRequired).thenReturn(tenantId);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── Groups ──

    @Test
    void createGroup_whenValidRequest_shouldReturnResponse() {
        ModifierGroupRequest request = new ModifierGroupRequest("Ponto da carne", 0, 1, true, null);
        ModifierGroup savedGroup = ModifierGroup.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Ponto da carne")
                .minSelect(0)
                .maxSelect(1)
                .active(true)
                .options(new ArrayList<>())
                .build();

        when(groupRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.name())).thenReturn(false);
        when(groupRepository.save(any(ModifierGroup.class))).thenReturn(savedGroup);

        ModifierGroupResponse response = modifierService.createGroup(request);

        assertNotNull(response);
        assertEquals("Ponto da carne", response.name());
        assertEquals(0, response.minSelect());
        assertEquals(1, response.maxSelect());
    }

    @Test
    void createGroup_whenNameAlreadyExists_shouldThrowConflict() {
        ModifierGroupRequest request = new ModifierGroupRequest("Ponto da carne", 0, 1, true, null);

        when(groupRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.name())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.createGroup(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createGroup_whenMinSelectGreaterThanMaxSelect_shouldThrowBadRequest() {
        ModifierGroupRequest request = new ModifierGroupRequest("Ponto da carne", 2, 1, true, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.createGroup(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void deactivateGroup_whenLinkedToActiveProducts_shouldThrowConflict() {
        UUID groupId = UUID.randomUUID();
        ModifierGroup group = ModifierGroup.builder().id(groupId).tenantId(tenantId).build();

        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.of(group));
        when(productModifierGroupRepository.countByIdModifierGroupId(groupId)).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.deactivateGroup(groupId));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void getGroupById_whenNotFound_shouldThrowNotFound() {
        UUID groupId = UUID.randomUUID();
        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.getGroupById(groupId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ── Options ──

    @Test
    void addOption_whenGroupNotBelongsToTenant_shouldThrowNotFound() {
        UUID groupId = UUID.randomUUID();
        ModifierOptionRequest request = new ModifierOptionRequest("Ao ponto", BigDecimal.ZERO, 1, 0, true);

        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.addOption(groupId, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void addOption_whenValidRequest_shouldReturnResponse() {
        UUID groupId = UUID.randomUUID();
        ModifierGroup group = ModifierGroup.builder().id(groupId).tenantId(tenantId).options(new ArrayList<>()).build();
        ModifierOptionRequest request = new ModifierOptionRequest("Ao ponto", BigDecimal.ZERO, 1, 0, true);

        ModifierOption savedOption = ModifierOption.builder()
                .id(UUID.randomUUID())
                .modifierGroup(group)
                .name("Ao ponto")
                .priceAddition(BigDecimal.ZERO)
                .maxQuantity(1)
                .position(0)
                .active(true)
                .build();

        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.of(group));
        when(optionRepository.save(any(ModifierOption.class))).thenReturn(savedOption);

        ModifierOptionResponse response = modifierService.addOption(groupId, request);

        assertNotNull(response);
        assertEquals("Ao ponto", response.name());
        verify(groupRepository).save(group);
    }

    @Test
    void removeOption_whenWouldViolateMinSelect_shouldThrowConflict() {
        UUID groupId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();

        ModifierOption option1 = ModifierOption.builder().id(optionId).active(true).build();
        ModifierGroup group = ModifierGroup.builder()
                .id(groupId)
                .tenantId(tenantId)
                .minSelect(1)
                .options(new ArrayList<>(List.of(option1)))
                .build();
        option1.setModifierGroup(group);

        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.of(group));
        when(optionRepository.findByIdWithGroup(optionId)).thenReturn(Optional.of(option1));

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.removeOption(groupId, optionId));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ── Links ──

    @Test
    void linkGroupToProduct_whenAlreadyLinked_shouldThrowConflict() {
        UUID productId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Product product = Product.builder().id(productId).tenantId(tenantId).build();
        ModifierGroup group = ModifierGroup.builder().id(groupId).tenantId(tenantId).build();

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(product));
        when(groupRepository.findByIdAndTenantId(groupId, tenantId)).thenReturn(Optional.of(group));
        when(productModifierGroupRepository.existsById(new ProductModifierGroupId(productId, groupId))).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> modifierService.linkGroupToProduct(productId, groupId, 0));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void listProductModifiers_whenProductHasNoGroups_shouldReturnEmptyList() {
        UUID productId = UUID.randomUUID();

        when(groupRepository.findByProductIdWithOptions(productId, tenantId)).thenReturn(List.of());

        List<ModifierGroupResponse> responses = modifierService.listProductModifiers(productId);

        assertTrue(responses.isEmpty());
    }
}