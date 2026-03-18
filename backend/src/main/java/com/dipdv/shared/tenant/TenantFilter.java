package com.dipdv.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter responsável por injetar o tenant_id no contexto da transação PostgreSQL.
 *
 * FLUXO:
 *   JwtAuthFilter popula TenantContext  →  TenantFilter lê TenantContext
 *   e executa: SET LOCAL app.current_tenant = '<uuid>'
 *   →  PostgreSQL RLS policies leem: current_setting('app.current_tenant', true)
 *
 * POR QUE "SET LOCAL" E NÃO "SET"?
 *   SET LOCAL aplica o valor apenas à transação atual.
 *   Ao fim da transação, o valor é descartado automaticamente.
 *   SET (sem LOCAL) afetaria a conexão inteira — perigoso com pool de conexões
 *   (HikariCP), onde a mesma conexão é reutilizada por requests diferentes.
 *
 * ORDEM NA FILTER CHAIN:
 *   JwtAuthFilter → TenantFilter → Controller → Service (@Transactional)
 *   O SET LOCAL precisa ocorrer dentro da mesma transação do Service.
 *   Por isso o @Transactional está aqui no Filter — garante que o SET LOCAL
 *   e as queries do Service compartilhem a mesma conexão/transação.
 *
 * ROTAS PÚBLICAS (/auth/**):
 *   TenantContext.get() retorna null para requests sem JWT.
 *   O filter só executa o SET LOCAL se o tenantId estiver presente,
 *   evitando erro em rotas públicas.
 */
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        UUID tenantId = TenantContext.get();

        if (tenantId != null) {
            // Injeta o tenant_id na transação atual do PostgreSQL
            // O RLS vai usar esse valor em todas as queries da thread
            entityManager
                .createNativeQuery("SET LOCAL app.current_tenant = :tenantId")
                .setParameter("tenantId", tenantId.toString())
                .executeUpdate();

            log.debug("TenantContext ativado para tenant={}", tenantId);
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
