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

    @Value("${dipdv.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Autentica um usuário e retorna um JWT.
     *
     * FLUXO:
     * 1. Busca usuário ativo pelo email + tenantId
     * 2. Valida a senha com BCrypt
     * 3. Gera JWT com claims: userId, tenantId, role
     * 4. Retorna AuthResponse com token e dados do usuário
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

        // Verificar status do tenant antes de validar credenciais
        tenantRepository.findById(request.tenantId()).ifPresent(tenant -> {
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

        User user = userRepository
            .findActiveByEmailAndTenantId(request.email(), request.tenantId())
            .orElseThrow(() -> {
                log.warn("Tentativa de login com email não encontrado: {} tenant: {}",
                    request.email(), request.tenantId());
                return new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Senha incorreta para usuário: {} tenant: {}",
                user.getId(), request.tenantId());
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

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
    }
}
