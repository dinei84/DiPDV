package com.dipdv.shared.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter responsável por injetar o tenant_id no contexto da transação PostgreSQL.
 *
 * FLUXO:
 *   JwtAuthFilter popula TenantContext  →  TenantFilter lê TenantContext
 *   e delega para TenantContextService que executa:
 *   SET LOCAL app.current_tenant = '<uuid>'
 *   →  PostgreSQL RLS policies leem: current_setting('app.current_tenant', true)
 *
 * POR QUE DELEGAR PARA UM SERVICE?
 *   @Transactional em métodos protected de OncePerRequestFilter não funciona com CGLIB.
 *   O Spring não consegue criar proxy de filtros do Tomcat (contexto marcado como final).
 *   A lógica transacional foi extraída para TenantContextService — padrão correto.
 *
 * ORDEM NA FILTER CHAIN:
 *   JwtAuthFilter → TenantFilter → Controller → Service (@Transactional)
 *
 * ROTAS PÚBLICAS (/auth/**):
 *   TenantContext.get() retorna null para requests sem JWT.
 *   O filter só executa o SET LOCAL se o tenantId estiver presente,
 *   evitando erro em rotas públicas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final TenantContextService tenantContextService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        UUID tenantId = TenantContext.get();
        final UUID MASTER_TENANT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        if (tenantId != null) {
            // Delega para o Service que executa o SET LOCAL dentro de uma transação gerenciada
            if (tenantId.equals(MASTER_TENANT_ID)) {
                tenantContextService.applyTenantContextSuperAdmin(tenantId);
            } else {
                tenantContextService.applyTenantContext(tenantId);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Não filtrar requisições de assets estáticos ou atuator
     * (esses endpoints não precisam de contexto de tenant)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/api-docs");
    }
}
