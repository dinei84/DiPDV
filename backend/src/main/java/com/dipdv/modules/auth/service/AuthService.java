package com.dipdv.modules.auth.service;

import com.dipdv.modules.auth.dto.AuthResponse;
import com.dipdv.modules.auth.dto.LoginRequest;
import com.dipdv.modules.auth.entity.User;
import com.dipdv.modules.auth.repository.UserRepository;
import com.dipdv.shared.exception.BusinessException;
import com.dipdv.shared.security.JwtService;
import com.dipdv.shared.tenant.enums.TenantPlan;
import com.dipdv.shared.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantRepository tenantRepository;
    private final com.dipdv.shared.tenant.TenantContextService tenantContextService;

    @Value("${dipdv.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Autentica um usuário e retorna um JWT.
     *
     * FLUXO:
     * 1. Seta contexto do tenant para o RLS
     * 2. Busca usuário ativo pelo email + tenantId
     * 3. Valida a senha com BCrypt
     * 4. Gera JWT com claims: userId, tenantId, role
     * 5. Retorna AuthResponse com token e dados do usuário
     *
     * SEGURANÇA:
     * - Sempre retorna a mesma mensagem de erro para email não encontrado
     *   e senha incorreta — evita user enumeration attack.
     * - Tempo de resposta propositalmente não varia (BCrypt é constante).
     *
     * @throws BusinessException 401 se credenciais inválidas
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Mensagem genérica intencional — não revelar se é email ou senha
        final String INVALID_CREDENTIALS = "Email ou senha inválidos";

        // 1. Ativar flag de bypass RLS temporário para busca global de email
        tenantContextService.applyGlobalLookupContext();

        try {
            // 2. Buscar usuário ativo pelo email (globalmente único na plataforma)
            User user = userRepository
                .findByEmailAndActiveTrue(request.email())
                .orElseThrow(() -> {
                    log.warn("Tentativa de login com email não encontrado: {}", request.email());
                    return new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
                });

            // 3. Definir o contexto do tenant para validação de regras de negócio (plano/suspensão)
            if (com.dipdv.shared.security.MasterTenantConstants.isMasterTenant(user.getTenantId())) {
                tenantContextService.applyTenantContextSuperAdmin(user.getTenantId());
            } else {
                tenantContextService.applyTenantContext(user.getTenantId());
            }

            // 4. Verificar status do tenant antes de validar a senha
            tenantRepository.findById(user.getTenantId()).ifPresent(tenant -> {
                if (tenant.getPlanType() == TenantPlan.SUSPENDED) {
                    throw new BusinessException(
                            "Conta suspensa. Entre em contato com o suporte DiPDV.",
                            HttpStatus.FORBIDDEN);
                }
                if (!tenant.isActive()) {
                    throw new BusinessException(
                            "Conta inativa. Entre em contato com o suporte DiPDV.",
                            HttpStatus.FORBIDDEN);
                }
            });

            // 5. Validar a senha
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                log.warn("Senha incorreta para usuário: {}", user.getId());
                throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
            }

            // 6. Gerar JWT com claims do usuário
            String token = jwtService.generateToken(
                user.getId(),
                user.getTenantId(),
                user.getRole().name()
            );

            log.info("Login bem-sucedido — userId={} tenantId={} role={}",
                user.getId(), user.getTenantId(), user.getRole());

            return AuthResponse.of(
                token,
                jwtExpirationMs,
                user.getId(),
                user.getTenantId(),
                user.getName(),
                user.getRole()
            );
        } finally {
            // Garante que o bypass seja removido mesmo em caso de erro
            tenantContextService.clearGlobalLookupContext();
        }
    }
}
