package com.dipdv.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço auxiliar para executar o SET LOCAL do RLS dentro de uma transação gerenciada.
 *
 * POR QUE UM SERVICE SEPARADO?
 *   O @Transactional em métodos protected de OncePerRequestFilter não funciona com CGLIB.
 *   O Spring não consegue criar proxy de filtros do Tomcat (contexto marcado como final).
 *   Extrair a lógica transacional para um @Service é o padrão correto para este cenário.
 *
 *   O TenantFilter delega para cá — a transação é aberta no Service e o EntityManager
 *   executa o SET LOCAL dentro dessa mesma transação, garantindo o escopo correto para o RLS.
 */
@Slf4j
@Service
public class TenantContextService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Injeta o tenant_id na transação atual do PostgreSQL via SET LOCAL.
     * O RLS usa esse valor em todas as queries da mesma transação.
     *
     * SET LOCAL garante que o valor seja descartado ao fim da transação,
     * evitando vazamento entre requests no pool de conexões (HikariCP).
     */
    @Transactional
    public void applyTenantContext(UUID tenantId) {
        entityManager
            .createNativeQuery("SET LOCAL app.current_tenant = :tenantId")
            .setParameter("tenantId", tenantId.toString())
            .executeUpdate();

        log.debug("TenantContext ativado para tenant={}", tenantId);
    }
}
