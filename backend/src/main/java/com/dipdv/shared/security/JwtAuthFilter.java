package com.dipdv.shared.security;

import com.dipdv.shared.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter que executa UMA VEZ por request (OncePerRequestFilter).
 *
 * RESPONSABILIDADES:
 * 1. Extrair o Bearer Token do header Authorization
 * 2. Validar assinatura e expiração via JwtService
 * 3. Popular o SecurityContext com o usuário autenticado
 * 4. Popular o TenantContext com o tenantId extraído do JWT
 *
 * ORDEM NA FILTER CHAIN (definida em SecurityConfig):
 *   JwtAuthFilter → antes do UsernamePasswordAuthenticationFilter
 *
 * TRATAMENTO DE ERROS:
 * - Token ausente → não popula contexto → Spring Security retorna 401
 * - Token inválido/expirado → loga e continua sem autenticação → 401
 * - Não lança exceção para não quebrar o fluxo do filter
 *
 * NOTA SOBRE TenantContext:
 * O clear() do TenantContext é feito aqui no finally{} porque este filter
 * é o responsável por definir o contexto. Garante limpeza mesmo em caso
 * de exceção no controller ou service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null) {
                authenticateRequest(token);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Limpeza obrigatória — Tomcat reutiliza threads
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    private void authenticateRequest(String token) {
        try {
            Claims claims = jwtService.validateAndExtractClaims(token);

            UUID userId   = jwtService.extractUserId(claims);
            UUID tenantId = jwtService.extractTenantId(claims);
            String role   = jwtService.extractRole(claims);

            // Se SUPER_ADMIN, setar flag no TenantContext para uso posterior
            if ("SUPER_ADMIN".equals(role)) {
                TenantContext.set(MasterTenantConstants.MASTER_TENANT_ID);
                // Nota: applyTenantContextSuperAdmin() é chamado pelos controllers admin
                // não aqui no Filter — o Filter apenas marca o contexto Java
            } else {
                // 1. Popular TenantContext — lido pelo TenantFilter para SET LOCAL
                TenantContext.set(tenantId);
            }

            // 2. Popular SecurityContext — lido pelo Spring Security e @PreAuthorize
            // O prefixo ROLE_ é exigido pelo Spring Security para hasRole()
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId.toString(),          // principal = userId como String
                    null,                       // credentials = null (já autenticado via JWT)
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );

            // Armazena detalhes adicionais acessíveis via @AuthenticationPrincipal
            authentication.setDetails(new DiPdvAuthDetails(userId, tenantId, role));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT autenticado — userId={} tenantId={} role={}", userId, tenantId, role);

        } catch (JwtException e) {
            // Não propaga exceção — deixa o Spring Security retornar 401
            log.warn("JWT inválido ou expirado: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Erro inesperado ao processar JWT", e);
        }
    }
}
