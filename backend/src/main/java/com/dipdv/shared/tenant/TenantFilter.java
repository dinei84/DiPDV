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
import org.springframework.transaction.support.TransactionTemplate;
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
 */
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;

    public TenantFilter(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        UUID tenantId = TenantContext.get();

        if (tenantId != null) {
            try {
                transactionTemplate.execute(status -> {
                    // Injeta o tenant_id na transação atual do PostgreSQL
                    entityManager
                        .createNativeQuery("SET LOCAL app.current_tenant = :tenantId")
                        .setParameter("tenantId", tenantId.toString())
                        .executeUpdate();

                    log.debug("TenantContext ativado para tenant={}", tenantId);

                    try {
                        filterChain.doFilter(request, response);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof ServletException) {
                    throw (ServletException) e.getCause();
                }
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Não filtrar requisições de assets estáticos ou atuator
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/api-docs");
    }
}
