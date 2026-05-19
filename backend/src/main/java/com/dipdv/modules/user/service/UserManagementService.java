package com.dipdv.modules.user.service;

import com.dipdv.modules.admin.dto.FirstAdminRequest;
import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.entity.enums.UserRole;
import com.dipdv.modules.auth.repository.UserRepository;
import com.dipdv.modules.user.dto.UserCreateRequest;
import com.dipdv.modules.user.dto.UserResponse;
import com.dipdv.modules.user.dto.UserUpdateRequest;
import com.dipdv.shared.audit.AuditAction;
import com.dipdv.shared.audit.Auditable;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.MasterTenantConstants;
import com.dipdv.shared.tenant.TenantContext;
import com.dipdv.shared.tenant.TenantContextService;
import com.dipdv.shared.tenant.entity.Tenant;
import com.dipdv.shared.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final EnumSet<UserRole> TENANT_ADMIN_ALLOWED_ROLES =
            EnumSet.of(UserRole.MANAGER, UserRole.CASHIER);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantContextService tenantContextService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @Auditable(action = AuditAction.USER_CREATED, entity = "users")
    public UserResponse createFirstAdmin(UUID tenantId, FirstAdminRequest request) {
        tenantContextService.applyTenantContextSuperAdmin(MasterTenantConstants.MASTER_TENANT_ID);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado", HttpStatus.NOT_FOUND));

        if (!tenant.isActive()) {
            throw new BusinessException("Tenant inativo", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByTenantIdAndEmailAndActiveTrue(tenantId, request.email())) {
            throw new BusinessException("Já existe um usuário ativo com este email", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.email())
                .name(request.name())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.ADMIN)
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(boolean includeInactive, Pageable pageable) {
        UUID tenantId = TenantContext.getRequired();
        Page<User> users = includeInactive
                ? userRepository.findByTenantIdOrderByNameAsc(tenantId, pageable)
                : userRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId, pageable);
        return users.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(findInCurrentTenant(id));
    }

    @Transactional
    @Auditable(action = AuditAction.USER_CREATED, entity = "users")
    public UserResponse createUser(UserCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        guardTenantAdminRole(request.role());

        if (userRepository.existsByTenantIdAndEmailAndActiveTrue(tenantId, request.email())) {
            throw new BusinessException("Já existe um usuário ativo com este email", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.email())
                .name(request.name())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional
    @Auditable(action = AuditAction.USER_UPDATED, entity = "users")
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        guardTenantAdminRole(request.role());
        User user = findInCurrentTenant(id);

        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("ADMIN não pode editar outro ADMIN", HttpStatus.FORBIDDEN);
        }

        user.setName(request.name());
        user.setRole(request.role());

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    @Auditable(action = AuditAction.USER_DEACTIVATED, entity = "users")
    public void deactivateUser(UUID id, UUID currentUserId) {
        if (currentUserId.equals(id)) {
            throw new BusinessException("Você não pode desativar sua própria conta", HttpStatus.CONFLICT);
        }

        User user = findInCurrentTenant(id);

        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("ADMIN não pode desativar outro ADMIN", HttpStatus.FORBIDDEN);
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    @Auditable(action = AuditAction.USER_REACTIVATED, entity = "users")
    public UserResponse reactivateUser(UUID id) {
        User user = findInCurrentTenant(id);

        if (user.isActive()) {
            return toResponse(user);
        }

        if (userRepository.existsByTenantIdAndEmailAndActiveTrue(user.getTenantId(), user.getEmail())) {
            throw new BusinessException("Já existe um usuário ativo com este email", HttpStatus.CONFLICT);
        }

        user.setActive(true);
        return toResponse(userRepository.save(user));
    }

    private User findInCurrentTenant(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado", HttpStatus.NOT_FOUND));
    }

    private void guardTenantAdminRole(UserRole role) {
        if (!TENANT_ADMIN_ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("Role não permitida para gestão de equipe", HttpStatus.FORBIDDEN);
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
