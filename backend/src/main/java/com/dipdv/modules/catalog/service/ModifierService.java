package com.dipdv.modules.catalog.service;

import com.dipdv.modules.catalog.dto.modifier.ModifierGroupRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierGroupResponse;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionRequest;
import com.dipdv.modules.catalog.dto.modifier.ModifierOptionResponse;
import com.dipdv.modules.catalog.entity.ModifierGroup;
import com.dipdv.modules.catalog.entity.ModifierOption;
import com.dipdv.modules.catalog.entity.Product;
import com.dipdv.modules.catalog.entity.ProductModifierGroup;
import com.dipdv.modules.catalog.entity.embedded.ProductModifierGroupId;
import com.dipdv.modules.catalog.repository.ModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ModifierOptionRepository;
import com.dipdv.modules.catalog.repository.ProductModifierGroupRepository;
import com.dipdv.modules.catalog.repository.ProductRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModifierService {

    private final ModifierGroupRepository groupRepository;
    private final ModifierOptionRepository optionRepository;
    private final ProductRepository productRepository;
    private final ProductModifierGroupRepository productModifierGroupRepository;

    // ── Modifier Groups ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ModifierGroupResponse> listGroups(Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        return groupRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId, pageable)
                .map(this::mapToGroupResponse);
    }

    @Transactional(readOnly = true)
    public ModifierGroupResponse getGroupById(UUID id) {
        return mapToGroupResponse(getGroupEntity(id));
    }

    @Transactional
    public ModifierGroupResponse createGroup(ModifierGroupRequest request) {
        UUID tenantId = TenantContext.getRequired();

        if (request.minSelect() > request.maxSelect()) {
            throw new BusinessException("Mínimo de seleção não pode ser maior que o máximo", HttpStatus.BAD_REQUEST);
        }

        if (groupRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.name())) {
            throw new BusinessException("Já existe um grupo de modificadores com este nome", HttpStatus.CONFLICT);
        }

        ModifierGroup group = ModifierGroup.builder()
                .tenantId(tenantId)
                .name(request.name())
                .minSelect(request.minSelect())
                .maxSelect(request.maxSelect())
                .active(request.active() != null ? request.active() : true)
                .build();

        if (request.options() != null && !request.options().isEmpty()) {
            List<ModifierOption> options = request.options().stream()
                    .map(optRequest -> createOptionEntity(group, optRequest))
                    .collect(Collectors.toList());
            group.getOptions().addAll(options);
        }

        return mapToGroupResponse(groupRepository.save(group));
    }

    @Transactional
    public ModifierGroupResponse updateGroup(UUID id, ModifierGroupRequest request) {
        ModifierGroup group = getGroupEntity(id);

        if (request.minSelect() > request.maxSelect()) {
            throw new BusinessException("Mínimo de seleção não pode ser maior que o máximo", HttpStatus.BAD_REQUEST);
        }

        if (!group.getName().equals(request.name()) &&
                groupRepository.existsByTenantIdAndNameAndActiveTrue(group.getTenantId(), request.name())) {
            throw new BusinessException("Já existe um grupo de modificadores com este nome", HttpStatus.CONFLICT);
        }

        group.setName(request.name());
        group.setMinSelect(request.minSelect());
        group.setMaxSelect(request.maxSelect());
        if (request.active() != null) {
            group.setActive(request.active());
        }

        return mapToGroupResponse(groupRepository.save(group));
    }

    @Transactional
    public void deactivateGroup(UUID id) {
        ModifierGroup group = getGroupEntity(id);
        
        long activeProductsLinked = productModifierGroupRepository.countByIdModifierGroupId(id);
        if (activeProductsLinked > 0) {
            throw new BusinessException(String.format("Grupo está vinculado a %d produto(s) ativo(s)", activeProductsLinked), HttpStatus.CONFLICT);
        }

        group.setActive(false);
        groupRepository.save(group);
    }

    // ── Modifier Options ─────────────────────────────────────────────────────

    @Transactional
    public ModifierOptionResponse addOption(UUID groupId, ModifierOptionRequest request) {
        ModifierGroup group = getGroupEntity(groupId);

        if (request.maxQuantity() < 1) {
            throw new BusinessException("Quantidade máxima deve ser pelo menos 1", HttpStatus.BAD_REQUEST);
        }

        ModifierOption option = createOptionEntity(group, request);
        group.getOptions().add(option);
        
        // Save the group to cascade the option creation
        groupRepository.save(group);
        
        // Returning the last added option response might be tricky after save due to ID generation,
        // So we explicitly save the option
        option = optionRepository.save(option);

        return mapToOptionResponse(option);
    }

    @Transactional
    public ModifierOptionResponse updateOption(UUID groupId, UUID optionId, ModifierOptionRequest request) {
        ModifierGroup group = getGroupEntity(groupId);

        ModifierOption option = optionRepository.findByIdWithGroup(optionId)
                .orElseThrow(() -> new BusinessException("Opção não encontrada", HttpStatus.NOT_FOUND));

        if (!option.getModifierGroup().getId().equals(groupId)) {
            throw new BusinessException("A opção não pertence ao grupo informado", HttpStatus.BAD_REQUEST);
        }

        option.setName(request.name());
        option.setPriceAddition(request.priceAddition());
        option.setMaxQuantity(request.maxQuantity());
        option.setPosition(request.position());
        if (request.active() != null) {
            option.setActive(request.active());
        }

        return mapToOptionResponse(optionRepository.save(option));
    }

    @Transactional
    public void removeOption(UUID groupId, UUID optionId) {
        ModifierGroup group = getGroupEntity(groupId);

        ModifierOption option = optionRepository.findByIdWithGroup(optionId)
                .orElseThrow(() -> new BusinessException("Opção não encontrada", HttpStatus.NOT_FOUND));

        if (!option.getModifierGroup().getId().equals(groupId)) {
            throw new BusinessException("A opção não pertence ao grupo informado", HttpStatus.BAD_REQUEST);
        }

        long activeOptionsCount = group.getOptions().stream().filter(ModifierOption::getActive).count();
        if (group.getMinSelect() > 0 && activeOptionsCount <= group.getMinSelect()) {
            throw new BusinessException("Não é possível remover a opção, o grupo atingiria um número de opções menor que o mínimo de seleção", HttpStatus.CONFLICT);
        }

        group.getOptions().remove(option);
        groupRepository.save(group);
    }

    // ── Vínculos Produto ↔ Grupo ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> listProductModifiers(UUID productId) {
        UUID tenantId = TenantContext.getRequired();
        List<ModifierGroup> groups = groupRepository.findByProductIdWithOptions(productId, tenantId);
        return groups.stream().map(this::mapToGroupResponse).collect(Collectors.toList());
    }

    @Transactional
    public void linkGroupToProduct(UUID productId, UUID groupId, Integer position) {
        UUID tenantId = TenantContext.getRequired();

        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));

        ModifierGroup group = getGroupEntity(groupId);

        ProductModifierGroupId linkId = new ProductModifierGroupId(productId, groupId);
        if (productModifierGroupRepository.existsById(linkId)) {
            throw new BusinessException("O grupo já está vinculado a este produto", HttpStatus.CONFLICT);
        }

        ProductModifierGroup link = ProductModifierGroup.builder()
                .id(linkId)
                .product(product)
                .modifierGroup(group)
                .position(position != null ? position : 0)
                .build();

        productModifierGroupRepository.save(link);
    }

    @Transactional
    public void unlinkGroupFromProduct(UUID productId, UUID groupId) {
        UUID tenantId = TenantContext.getRequired();

        productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado", HttpStatus.NOT_FOUND));

        getGroupEntity(groupId);

        ProductModifierGroupId linkId = new ProductModifierGroupId(productId, groupId);
        if (!productModifierGroupRepository.existsById(linkId)) {
            throw new BusinessException("O grupo não está vinculado a este produto", HttpStatus.NOT_FOUND);
        }

        productModifierGroupRepository.deleteById(linkId);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private ModifierGroup getGroupEntity(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return groupRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Grupo não encontrado", HttpStatus.NOT_FOUND));
    }

    private ModifierOption createOptionEntity(ModifierGroup group, ModifierOptionRequest request) {
        return ModifierOption.builder()
                .modifierGroup(group)
                .name(request.name())
                .priceAddition(request.priceAddition())
                .maxQuantity(request.maxQuantity())
                .position(request.position())
                .active(request.active() != null ? request.active() : true)
                .build();
    }

    private ModifierGroupResponse mapToGroupResponse(ModifierGroup group) {
        List<ModifierOptionResponse> optionResponses = group.getOptions() != null ?
                group.getOptions().stream()
                        .map(this::mapToOptionResponse)
                        .collect(Collectors.toList()) : List.of();

        return new ModifierGroupResponse(
                group.getId(),
                group.getName(),
                group.getMinSelect(),
                group.getMaxSelect(),
                group.getActive(),
                optionResponses
        );
    }

    private ModifierOptionResponse mapToOptionResponse(ModifierOption option) {
        return new ModifierOptionResponse(
                option.getId(),
                option.getName(),
                option.getPriceAddition(),
                option.getMaxQuantity(),
                option.getPosition(),
                option.getActive()
        );
    }
}